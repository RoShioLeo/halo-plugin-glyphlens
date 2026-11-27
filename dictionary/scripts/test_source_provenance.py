# SPDX-License-Identifier: MIT
from pathlib import Path
import copy
import json
import tempfile
import unittest
from source_provenance import SourceEvidence, compile_provenance


class ProvenanceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.cache = Path(self.temp.name)
        (self.cache / 'sources').mkdir()
        rows = [{'c': c, 's': index, 't': [{'c': t}]} for index, (c, t) in enumerate([('骂', '傌'), ('绸', '紬'), ('岂', '豈'), ('丁', '丁')], 1)]
        for file, data in [('sources/TGH-transcription.json', {'rows': rows}), ('properties.json', {'㐷': {'kTraditionalVariant': 'U+508C'}}), ('moe-relations.json', [])]:
            (self.cache / file).write_text(json.dumps(data, ensure_ascii=False), encoding='utf-8')
        (self.cache / 'sources/TSCharacters.txt').write_text('傌\t骂\n甲\t乙\n乙\t丙\n丙\t丁\n', encoding='utf-8')
        (self.cache / 'sources/STCharacters.txt').write_text('尽\t侭\n', encoding='utf-8')
        self.evidence = SourceEvidence(self.cache)

    def test_original_record_and_official_intermediate_are_both_retained(self):
        records = self.evidence.records('㐷', '骂')
        self.assertTrue(records)
        path = records[0]
        self.assertEqual(['㐷', '傌', '骂'], path['path'])
        self.assertIn('kTraditionalVariant', path['steps'][0]['locator'])
        self.assertEqual('㐷 → 傌', path['steps'][0]['raw'])
        self.assertEqual('mainland', path['steps'][1]['source'])

    def test_reverse_lookup_never_masquerades_as_a_direct_raw_record(self):
        step = self.evidence.records('侭', '尽')[0]['steps'][0]
        self.assertTrue(step['reversed'])
        self.assertEqual('尽 → 侭', step['raw'])
        self.assertEqual(('侭', '尽'), (step['from'], step['to']))
        self.assertTrue(step['url'].endswith('/STCharacters.txt#L1'))

    def test_alias_and_vertical_punctuation_keep_the_full_unicode_path(self):
        self.assertEqual(['豈', '豈', '岂'], self.evidence.records('豈', '岂')[0]['path'])
        record = self.evidence.records('︵', '（')[0]
        self.assertEqual(['︵', '(', '（'], record['path'])
        self.assertTrue(record['steps'][1]['reversed'])

    def test_retention_and_missing_evidence_are_not_invented_sources(self):
        self.assertEqual([], self.evidence.records('乾', '乾'))
        self.assertEqual('unresolved', self.evidence.records('龘', '甲')[0]['kind'])
        # General multi-hop traversal would incorrectly present this as proof.
        self.assertEqual('unresolved', self.evidence.records('甲', '丁')[0]['kind'])

    def test_compilation_keeps_original_sources_without_self_mapping_records(self):
        mapping = {'㐷': ['骂'], '乾': ['乾', '干'], '龘': ['甲']}
        original = copy.deepcopy(mapping)
        records = compile_provenance(self.cache, mapping)
        self.assertEqual(original, mapping)
        self.assertEqual('documented', records['㐷'][0]['kind'])
        self.assertFalse(any(record['candidate'] == c for c, values in records.items() for record in values))
        self.assertEqual('unresolved', records['龘'][0]['kind'])
        self.assertNotIn('maintainer-decisions', json.dumps(records))

    def test_manual_additions_never_claim_a_rejected_document_as_their_source(self):
        record = self.evidence.records('竃', '灶')[0]
        self.assertEqual('manual', record['kind'])
        self.assertEqual('手动维护来源', record['label'])
        self.assertNotIn('note', record)
        self.assertEqual([], record['steps'])
        self.assertNotIn('url', record)


if __name__ == '__main__':
    unittest.main()
