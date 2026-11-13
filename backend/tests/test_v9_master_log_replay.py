import sys
import json
import unittest
import zipfile
from pathlib import Path
from tempfile import TemporaryDirectory

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "app"))
from v9_master_log_replay import summarize_exports

class V9MasterLogReplayTest(unittest.TestCase):
    def test_latest_cumulative_export_not_double_counted(self):
        with TemporaryDirectory() as folder:
            paths = []
            for stamp, ids, mature, hits in [
                ("20261113-095805", [1], 1, 0),
                ("20261113-101129", [1, 2], 2, 1)
            ]:
                path = Path(folder)/("MultifyTraderPro-logs-v8.3.25-test-"+stamp+".zip")
                with zipfile.ZipFile(path,"w") as z:
                    z.writestr("fixed_recommendations.json",
                               json.dumps([{"id":x} for x in ids]))
                    z.writestr("fixed_sampled_maturity_and_repeat_research.json",
                        json.dumps({"sampled_target_hit_maturity":{"horizons":[{
                            "horizon_minutes":15,"matured_events":mature,
                            "all":{"full_target_hits":hits}}]}}))
                    z.writestr("system_audit.jsonl", json.dumps({
                        "category":"RUNTIME","event":"FIXED_SCAN_PROFILE",
                        "details":{"elapsed_ms":90000}}))
                    z.writestr("fixed_5m_shadow_pairs.json","[]")
                paths.append(path)
            study=summarize_exports(paths)
            self.assertEqual(1,len(study["compared_versions"]))
            row=study["compared_versions"][0]
            self.assertEqual(2,row["recommendations"])
            self.assertEqual(2,row["mature_15m"])
            self.assertEqual(1,row["target_hits_15m"])
            self.assertEqual(50.0,row["sampled_target_hit_rate_15m"])
            self.assertTrue(row["older_recommendation_ids_all_present"])
            self.assertIn("v8.3.16",study["missing_original_version_logs"])
            self.assertIsNone(study["best_strategy_selected"])

if __name__ == "__main__":
    unittest.main()
