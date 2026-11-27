// SPDX-License-Identifier: MIT
package cloud.lemonslice.glyph;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class DictionaryArchive {
    static final int MAX_UPLOAD=8*1024*1024,MAX_UNPACKED=32*1024*1024;
    static final List<String> DATA=List.of("dictionary.json","lexicon.json","sources.json","reviews.json","reference.json","version.json");
    private static final Set<String> DOCUMENTS=Set.of("README.md","FORMAT.md","NOTICE.md","LICENSE","licenses/Unicode-LICENSE.txt","licenses/OpenCC-LICENSE.txt");
    static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Map<String,byte[]> files;
    private final String version,fingerprint;
    private DictionaryArchive(Map<String,byte[]> files) {
        this.files=Map.copyOf(files);
        var metadata=tree(files.get("version.json"));
        version=metadata.path("version").asText();
        if(!version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)") || version.length()>32)throw UnicodeInput.invalid("字库版本须为 1.0.0 形式");
        var mapping=mappings(tree(files.get("dictionary.json")),false);
        var reviews=tree(files.get("reviews.json"));
        if(!reviews.isObject())throw UnicodeInput.invalid("待审索引格式无效");
        reviews.fields().forEachRemaining(field->{
            character(field.getKey(),false);var review=field.getValue();
            if(!review.isObject())throw UnicodeInput.invalid("待审索引须为逐字记录");
            candidates(review.path("candidates"));
            for(String key:List.of("reasons","evidence","links"))strings(review.path(key),"待审 "+key);
        });
        var actual=counts(mapping,reviews.size());
        if(!JSON.valueToTree(actual).equals(metadata.path("counts")))throw UnicodeInput.invalid("字库统计与实际映射不一致");
        var sources=tree(files.get("sources.json"));
        if(!sources.isObject() || !sources.path("entries").isObject() || !sources.path("links").isObject())throw UnicodeInput.invalid("来源索引格式无效");
        if(sources.has("confirmations") && !sources.get("confirmations").isObject())throw UnicodeInput.invalid("审核索引格式无效");
        sources.path("entries").fields().forEachRemaining(field->{
            character(field.getKey(),false);
            if(!field.getValue().isArray())throw UnicodeInput.invalid("来源须为逐字记录数组");
            for(var record:field.getValue()) {
                if(!record.isObject() || !record.path("label").isTextual())throw UnicodeInput.invalid("来源记录缺少名称");
                if(record.has("candidate"))character(record.get("candidate").asText(),false);
                if(record.has("steps") && !record.get("steps").isArray())throw UnicodeInput.invalid("来源路径格式无效");
                for(var step:record.path("steps"))if(!step.isObject() || !step.path("label").isTextual() || !step.path("url").isTextual() || !step.path("from").isTextual() || !step.path("to").isTextual())throw UnicodeInput.invalid("来源路径须含名称、链接和起止字形");
            }
        });
        sources.path("confirmations").fields().forEachRemaining(field->{character(field.getKey(),false);candidates(field.getValue().path("candidates"));});
        if(!version.equals(sources.path("summary").path("version").asText()) || !JSON.valueToTree(actual).equals(sources.path("summary").path("counts")))throw UnicodeInput.invalid("来源元数据的版本或统计不一致");
        var reference=tree(files.get("reference.json"));
        if(!reference.isObject() || !reference.path("mappings").isObject() || !reference.path("links").isObject())throw UnicodeInput.invalid("参考索引格式无效");
        reference.path("mappings").fields().forEachRemaining(field->{character(field.getKey(),false);candidates(field.getValue());});
        reference.path("links").fields().forEachRemaining(field->{character(field.getKey(),false);strings(field.getValue(),"参考链接");});
        links(sources,false);links(reference,false);links(reviews,false);
        var lexicon=tree(files.get("lexicon.json"));
        if(!lexicon.isArray() || lexicon.size()>5000)throw UnicodeInput.invalid("默认词典格式无效");
        var ids=new LinkedHashSet<String>();int contextCount=0;var lexiconCharacters=new LinkedHashSet<String>();
        for(var rule:lexicon) {
            String id=rule.path("id").asText(),source=UnicodeInput.character(rule.path("character").asText()),target=UnicodeInput.character(rule.path("candidate").asText());
            if(id.isBlank() || id.length()>100 || !ids.add(id))throw UnicodeInput.invalid("词典标识为空或重复");
            var candidates=mapping.getOrDefault(source,List.of());
            if(candidates.size()<2 || !candidates.contains(target))throw UnicodeInput.invalid("词典候选不在该原字的多候选映射中："+source);
            var contexts=rule.path("contexts");
            if(!contexts.isArray() || contexts.isEmpty() || contexts.size()>100)throw UnicodeInput.invalid("词典上下文格式无效");
            if(!rule.path("note").isTextual())throw UnicodeInput.invalid("默认词典缺少备注字段");
            strings(rule.path("sources"),"词典来源");
            for(var context:contexts)if(!context.isTextual() || context.asText().codePointCount(0,context.asText().length())<2 || context.asText().codePointCount(0,context.asText().length())>64 || !context.asText().contains(target))throw UnicodeInput.invalid("词典上下文须为包含候选字的 2 至 64 字文本");
            contextCount+=contexts.size();lexiconCharacters.add(source);
            links(rule,false);
        }
        if(contextCount>5000)throw UnicodeInput.invalid("默认消歧词条不能超过 5000 个");
        var lexiconCounts=JSON.valueToTree(Map.of("rules",lexicon.size(),"contexts",contextCount,"characters",lexiconCharacters.size()));
        if(!lexiconCounts.equals(metadata.path("lexicon")))throw UnicodeInput.invalid("默认词典统计不一致");
        try {
            JSON.convertValue(reviews,new TypeReference<Map<String,GlyphCatalog.Review>>() {});
            JSON.convertValue(reference,GlyphCatalog.Reference.class);
            JSON.convertValue(lexicon,new TypeReference<List<LexiconCatalog.Entry>>() {});
        }catch(IllegalArgumentException failure){throw UnicodeInput.invalid("字库索引或词典包含不支持的字段");}
        var digest=new StringBuilder();DATA.stream().sorted().forEach(name->digest.append(name).append(':').append(sha(files.get(name))).append('\n'));
        fingerprint=sha(digest.toString().getBytes(StandardCharsets.UTF_8));
    }
    static DictionaryArchive read(byte[] bytes) {
        if(bytes.length>MAX_UPLOAD)throw UnicodeInput.invalid("字库文件不能超过 8 MiB");
        var files=new LinkedHashMap<String,byte[]>();var seen=new LinkedHashSet<String>();int total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(name.equals("licenses/") && entry.isDirectory())continue;
                if(!DATA.contains(name) && !name.equals("manifest.json") && !DOCUMENTS.contains(name))throw UnicodeInput.invalid("字库包包含不支持的文件路径："+name);
                if(entry.isDirectory() || !seen.add(name))throw UnicodeInput.invalid("字库包文件重复或无效");
                var buffer=new ByteArrayOutputStream();byte[] chunk=new byte[8192];int read;
                while((read=zip.read(chunk))!=-1){total+=read;if(total>MAX_UNPACKED)throw UnicodeInput.invalid("字库包解压数据不能超过 32 MiB");buffer.write(chunk,0,read);}
                files.put(name,buffer.toByteArray());
            }
        }catch(IOException failure){throw UnicodeInput.invalid("无法读取 ZIP 字库包");}
        if(!files.keySet().containsAll(DATA) || !files.containsKey("manifest.json"))throw UnicodeInput.invalid("字库包缺少数据或 manifest.json");
        if(!files.containsKey("LICENSE") || !files.containsKey("NOTICE.md"))throw UnicodeInput.invalid("字库包须保留 LICENSE 和 NOTICE.md");
        var manifest=tree(files.remove("manifest.json"));
        if(!"glyphlens-dictionary".equals(manifest.path("format").asText()) || !manifest.path("formatVersion").isIntegralNumber() || manifest.path("formatVersion").asInt()!=1)throw UnicodeInput.invalid("不支持的字库包格式版本");
        var hashes=manifest.path("files");
        if(!hashes.isObject() || hashes.size()!=DATA.size())throw UnicodeInput.invalid("字库包校验清单无效");
        for(String name:DATA)if(!sha(files.get(name)).equals(hashes.path(name).asText()))throw UnicodeInput.invalid("字库文件校验失败："+name);
        var result=new DictionaryArchive(files);
        if(!result.version.equals(manifest.path("dictionaryVersion").asText()))throw UnicodeInput.invalid("字库清单版本不一致");
        return result;
    }
    static DictionaryArchive fromCatalog(GlyphCatalog catalog) {return new DictionaryArchive(catalog.files());}
    byte[] zip() {
        var manifest=new LinkedHashMap<String,Object>();manifest.put("format","glyphlens-dictionary");manifest.put("formatVersion",1);manifest.put("dictionaryVersion",version);
        var hashes=new LinkedHashMap<String,String>();DATA.forEach(name->hashes.put(name,sha(files.get(name))));manifest.put("files",hashes);
        try(var bytes=new ByteArrayOutputStream();var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            for(String name:DATA){zip.putNextEntry(entry(name));zip.write(files.get(name));zip.closeEntry();}
            zip.putNextEntry(entry("manifest.json"));zip.write(JSON.writeValueAsBytes(manifest));zip.closeEntry();
            for(String name:DOCUMENTS.stream().sorted().toList())try(var resource=DictionaryArchive.class.getClassLoader().getResourceAsStream("dictionary/"+name)) {
                if(files.containsKey(name) || resource!=null){zip.putNextEntry(entry(name));if(files.containsKey(name))zip.write(files.get(name));else resource.transferTo(zip);zip.closeEntry();}
            }
            zip.finish();return bytes.toByteArray();
        }catch(IOException failure){throw new IllegalStateException("无法导出默认字库包",failure);}
    }
    private static ZipEntry entry(String name){var entry=new ZipEntry(name);entry.setTime(0);return entry;}
    String version(){return version;}
    String fingerprint(){return fingerprint;}
    Map<String,byte[]> files(){return files;}
    GlyphCatalog catalog(){return new GlyphCatalog(files);}
    static JsonNode tree(byte[] bytes) {
        try {var result=JSON.readTree(bytes);if(result==null)throw UnicodeInput.invalid("JSON 文件不能为空");return result;}
        catch(IOException failure){throw UnicodeInput.invalid("JSON 格式无效，请检查编码、重复字段和语法");}
    }
    static Map<String,List<String>> mappings(JsonNode node,boolean allowHex) {
        if(!node.isObject() || node.isEmpty() || node.size()>50000)throw UnicodeInput.invalid("映射表须为包含 1 至 50000 个原字的 JSON 对象");
        var result=new LinkedHashMap<String,List<String>>();
        node.fields().forEachRemaining(field->{
            String source=character(field.getKey(),allowHex);var values=field.getValue();
            if(!values.isArray() || values.isEmpty() || values.size()>50)throw UnicodeInput.invalid("每个原字须有 1 至 50 个候选："+source);
            var candidates=new ArrayList<String>();
            for(var value:values){if(!value.isTextual())throw UnicodeInput.invalid("候选须为单个字符或 Unicode 码");String candidate=character(value.asText(),allowHex);if(candidates.contains(candidate))throw UnicodeInput.invalid("候选重复："+source);candidates.add(candidate);}
            if(result.put(source,List.copyOf(candidates))!=null)throw UnicodeInput.invalid("归一后原字重复："+source);
        });return result;
    }
    private static String character(String value,boolean allowHex) {
        if(!allowHex && value.codePointCount(0,value.length())!=1)throw UnicodeInput.invalid("默认字库数据须使用实际单字");
        return UnicodeInput.character(value);
    }
    private static void strings(JsonNode value,String label) {
        if(!value.isArray())throw UnicodeInput.invalid(label+" 须为文本数组");
        for(var item:value)if(!item.isTextual())throw UnicodeInput.invalid(label+" 须为文本数组");
    }
    private static void candidates(JsonNode values) {
        strings(values,"候选");if(values.isEmpty() || values.size()>50)throw UnicodeInput.invalid("候选数量须为 1 至 50 个");
        var unique=new LinkedHashSet<String>();for(var value:values)if(!unique.add(character(value.asText(),false)))throw UnicodeInput.invalid("候选重复");
    }
    static Map<String,Integer> counts(Map<String,List<String>> mapping,int pending) {
        return Map.of("records",mapping.size(),"relations",mapping.values().stream().mapToInt(List::size).sum(),"multi",(int)mapping.values().stream().filter(values->values.size()>1).count(),"pending",pending);
    }
    private static void links(JsonNode value,boolean url) {links(value,url,0);}
    private static void links(JsonNode value,boolean url,int depth) {
        if(depth>32)throw UnicodeInput.invalid("来源数据嵌套过深");
        if(value.isTextual() && url) {
            try{var uri=URI.create(value.asText());if(!Set.of("https","http").contains(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null)throw new IllegalArgumentException();}
            catch(IllegalArgumentException failure){throw UnicodeInput.invalid("来源链接须为有效的 HTTP(S) 地址");}
        } else if(value.isArray())value.forEach(child->links(child,url,depth+1));
        else if(value.isObject())value.fields().forEachRemaining(field->links(field.getValue(),url || Set.of("url","links","sources").contains(field.getKey()),depth+1));
        else if(url)throw UnicodeInput.invalid("来源链接须为文本地址");
    }
    static String sha(byte[] bytes) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
