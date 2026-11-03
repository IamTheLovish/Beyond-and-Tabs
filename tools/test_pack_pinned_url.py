"""pack/build_pack.py: a required mod that is not on Modrinth (Domum Ornamentum, CurseForge-only) is pinned in
pack/instance_mods.json with its official "url". The pack points testers at that URL only if the bytes there are the
pinned file, and only on an official host - never a rehost."""
import hashlib
import os
import sys
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "pack"))
import build_pack  # noqa: E402

BYTES = b"domum jar"
PIN = {"file": "domum_ornamentum-1.20.1-1.0.304-snapshot-universal.jar", "size": len(BYTES),
       "sha1": hashlib.sha1(BYTES).hexdigest(), "sha512": hashlib.sha512(BYTES).hexdigest(),
       "url": "https://edge.forgecdn.net/files/8765/962/domum_ornamentum-1.20.1-1.0.304-snapshot-universal.jar"}


def serving(data):
    def download(url, dest):
        with open(dest, "wb") as f:
            f.write(data)
    return download


class PinnedUrl(unittest.TestCase):
    def test_the_pinned_bytes_become_a_pack_entry_at_that_url(self):
        with mock.patch.object(build_pack, "download", serving(BYTES)):
            e = build_pack.resolve_pinned_url(PIN)
        self.assertEqual(e["downloads"], [PIN["url"]])
        self.assertEqual(e["hashes"], {"sha1": PIN["sha1"], "sha512": PIN["sha512"]})
        self.assertEqual(e["env"]["server"], "required")   # the dedicated server needs it too

    def test_other_bytes_stop_the_build(self):
        with mock.patch.object(build_pack, "download", serving(b"something else")), self.assertRaises(SystemExit):
            build_pack.resolve_pinned_url(PIN)

    def test_an_unofficial_host_stops_the_build(self):
        with mock.patch.object(build_pack, "download", serving(BYTES)), self.assertRaises(SystemExit):
            build_pack.resolve_pinned_url(dict(PIN, url="https://example.com/domum.jar"))


if __name__ == "__main__":
    unittest.main()
