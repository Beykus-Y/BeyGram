"""Keep CameraX runtime artifacts on one stable release.

Run with: python3 -m unittest discover -s scripts -p 'test_*.py'
The Android CI job already runs this command before compiling the APK.
"""
from pathlib import Path
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "gradle" / "libs.versions.toml"
GRADLE_APP = ROOT / "app" / "build.gradle.kts"
CAMERA_MODULES = {
    "camera-camera2",
    "camera-core",
    "camera-lifecycle",
    "camera-video",
    "camera-view",
}


class CameraXDependencyAlignmentTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.catalog = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
        cls.app = GRADLE_APP.read_text(encoding="utf-8")

    def test_camera_x_pinned_to_requested_stable_version(self):
        self.assertEqual("1.6.2", self.catalog["versions"]["camerax"])

    def test_all_camera_x_artifacts_share_one_version(self):
        camera_aliases = {
            alias: dep for alias, dep in self.catalog["libraries"].items()
            if dep.get("group") == "androidx.camera"
        }
        self.assertEqual(CAMERA_MODULES, {dep["name"] for dep in camera_aliases.values()})
        for alias, dep in camera_aliases.items():
            with self.subTest(alias=alias):
                self.assertEqual({"ref": "camerax"}, dep["version"])
                # Pinning only the catalog is not enough if the app accidentally stops using it.
                self.assertIn(f"implementation(libs.{alias.replace('-', '.')})", self.app)

    def test_diagnostics_reports_same_camera_x_version_as_runtime(self):
        self.assertIn(
            'buildConfigField("String", "CAMERAX_VERSION", libs.versions.camerax.get().asBuildConfigString())',
            self.app,
        )


if __name__ == "__main__":
    unittest.main()
