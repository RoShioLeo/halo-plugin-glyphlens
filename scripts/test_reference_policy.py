import unittest
from reference_policy import select_reference


class ReferencePolicyTest(unittest.TestCase):
    def select(self, pairs, regular=None, edges=None, pending=None, levels=None):
        return select_reference(
            regular or [{'character':'甲','code':'A1'},{'character':'乙','code':'B1'},{'character':'丙','code':'C1'}],
            [{'character':s,'regular_character':t,'regular_id':str(i)} for i,(s,t) in enumerate(pairs)],
            levels or {'甲':1,'乙':2,'丙':3},set(),edges or {'v2s':{},'v2t':{}},
            pending or {'v2s':{},'v2t':{}},{},{})

    def test_unique_common_targets_auto_enable_but_uncommon_does_not(self):
        auto,_,_=self.select([('𠀀','甲'),('𠀁','乙'),('𠀂','丙')])
        for direction in ['v2s','v2t']:
            self.assertEqual(auto[direction],{'𠀀':['甲'],'𠀁':['乙']})

    def test_protected_source_stays_reference_only(self):
        auto,ref,_=self.select([('丙','甲'),('甲','乙')])
        self.assertEqual(auto,{'v2s':{},'v2t':{}})
        self.assertEqual(ref['mappings']['v2s']['丙'],['甲'])

    def test_ambiguity_cannot_be_hidden_by_removing_rare_or_unencoded_target(self):
        auto,_,_=self.select([('𠀀','甲'),('𠀀','丙'),('𠀁','乙'),('𠀁',None)])
        self.assertEqual(auto,{'v2s':{},'v2t':{}})

    def test_old_mapping_and_conflicting_review_take_priority(self):
        edges={'v2s':{'𠀀':{'乙'}},'v2t':{}}
        pending={'v2s':{},'v2t':{'𠀀':{'丙'}}}
        auto,_,_=self.select([('𠀀','甲')],edges=edges,pending=pending)
        self.assertEqual(auto,{'v2s':{},'v2t':{}})
        self.assertEqual(edges['v2s']['𠀀'],{'乙'})

    def test_matching_review_can_become_active_without_manual_confirmation(self):
        auto,_,_=self.select([('𠀀','甲')],pending={'v2s':{'𠀀':{'甲'}},'v2t':{}})
        self.assertEqual(auto['v2s']['𠀀'],['甲'])

    def test_ambiguous_simplification_is_not_made_unique_by_common_filter(self):
        auto,_,_=select_reference([{'character':'乙','code':'A1'}],
            [{'character':'𠀀','regular_character':'乙','regular_id':'1'}],
            {'甲':1,'丙':3},set(),{'v2s':{},'v2t':{}},{'v2s':{},'v2t':{}},
            {'乙':{'甲','丙'}},{})
        self.assertNotIn('𠀀',auto['v2s'])
        self.assertEqual(auto['v2t']['𠀀'],['乙'])


if __name__ == '__main__':
    unittest.main()
