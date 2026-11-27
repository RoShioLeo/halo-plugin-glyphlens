import unittest
from reference_policy import select_reference

class ReferencePolicyTest(unittest.TestCase):
    def select(self,pairs,edges=None,pending=None):
        return select_reference([{'character':'甲','code':'A1'},{'character':'乙','code':'B1'},{'character':'丙','code':'C1'}],
            [{'character':s,'regular_character':t,'regular_id':str(i)} for i,(s,t) in enumerate(pairs)],
            {'甲':1,'乙':2,'丙':3},set(),edges or {},pending or {},{},{})
    def test_unique_common_target_is_active_but_uncommon_is_not(self):
        auto,_,_=self.select([('𠀀','甲'),('𠀁','乙'),('𠀂','丙')]);self.assertEqual(auto,{'𠀀':['甲'],'𠀁':['乙']})
    def test_protected_source_is_reference_only(self):
        auto,ref,_=self.select([('丙','甲'),('甲','乙')]);self.assertEqual(auto,{});self.assertEqual(ref['mappings']['丙'],['甲'])
    def test_rare_or_unencoded_target_cannot_hide_ambiguity(self):
        auto,_,_=self.select([('𠀀','甲'),('𠀀','丙'),('𠀁','乙'),('𠀁',None)]);self.assertEqual(auto,{})
    def test_existing_rule_or_conflicting_proposal_cannot_be_overwritten(self):
        auto,_,_=self.select([('𠀀','甲'),('𠀁','甲')],edges={'𠀀':{'乙'}},pending={'𠀁':{'丙'}});self.assertEqual(auto,{})
    def test_matching_proposal_can_become_active(self):
        auto,_,_=self.select([('𠀀','甲')],pending={'𠀀':{'甲'}});self.assertEqual(auto,{'𠀀':['甲']})
    def test_common_filter_cannot_make_ambiguous_simplification_unique(self):
        auto,_,_=select_reference([{'character':'乙','code':'A1'}],[{'character':'𠀀','regular_character':'乙','regular_id':'1'}],
            {'甲':1,'丙':3},set(),{},{},{'乙':{'甲','丙'}},{})
        self.assertEqual(auto,{})

if __name__=='__main__':unittest.main()
