"""The shader part of publish-pack.yml's "Test - a fresh tester install gets this exact build" step.

After packwiz-installer has filled a fresh tester instance from the commit just published:
- Windows: shaderpacks/ holds exactly our pack (shaderpacks/BeyondAndTabs, every file byte for byte as in this repo,
  nothing else), and Oculus' config (config/oculus.properties and config/iris.properties) is ON with our pack picked.
- Mac: no shader pack at all and no Oculus config (the Mac pack has no Oculus: it needs Embeddium).
The old guard failed any install with a shaderpacks folder, so after #554 every publish from main went red after the
push and skipped the keep-last-3 cleanup. Tested offline by tools/test_pack_tester_install.py.
Run: python3 pack/check_tester_install.py <windows|mac> <instance's minecraft dir> <repo's shaderpacks/BeyondAndTabs>
"""
import os
import sys

PACK = "BeyondAndTabs"
CONFIGS = ("config/oculus.properties", "config/iris.properties")


def files_under(root):
    """{path relative to root, / separated: bytes} of every file under root ({} when root is missing)."""
    out = {}
    for base, _, names in os.walk(root):
        for n in names:
            p = os.path.join(base, n)
            with open(p, "rb") as f:
                out[os.path.relpath(p, root).replace(os.sep, "/")] = f.read()
    return out


def properties(path):
    with open(path, encoding="utf-8") as f:
        return dict(line.strip().split("=", 1) for line in f if "=" in line and not line.lstrip().startswith("#"))


def check(variant, install, repo_pack):
    """The list of problems with the install (empty = fine)."""
    errors = []
    shipped = files_under(os.path.join(install, "shaderpacks"))
    if variant == "mac":
        if shipped:
            errors.append("mac pack has a shader pack: %s" % ", ".join(sorted(shipped)[:5]))
        for cfg in CONFIGS:
            if os.path.exists(os.path.join(install, cfg)):
                errors.append("mac pack has %s" % cfg)
        return errors
    if variant != "windows":
        return ["unknown variant %r" % variant]
    ours = {PACK + "/" + k: v for k, v in files_under(repo_pack).items()}
    if not ours:
        return ["the repo's shader pack %s has no files" % repo_pack]
    for rel in sorted(set(shipped) - set(ours)):
        errors.append("windows pack has shaderpacks/%s, which is not our pack's" % rel)
    for rel in sorted(set(ours) - set(shipped)):
        errors.append("windows pack is missing shaderpacks/%s" % rel)
    for rel in sorted(set(ours) & set(shipped)):
        if ours[rel] != shipped[rel]:
            errors.append("windows pack's shaderpacks/%s differs from the repo's" % rel)
    for cfg in CONFIGS:
        path = os.path.join(install, cfg)
        if not os.path.isfile(path):
            errors.append("windows pack has no %s" % cfg)
            continue
        props = properties(path)
        if props.get("enableShaders") != "true" or props.get("shaderPack") != PACK:
            errors.append("windows pack's %s is not ON with %s (enableShaders=%s, shaderPack=%s)"
                          % (cfg, PACK, props.get("enableShaders"), props.get("shaderPack")))
    return errors


def main(argv):
    if len(argv) != 4:
        print(__doc__)
        return 2
    errors = check(argv[1], argv[2], argv[3])
    for e in errors:
        print("::error::" + e)
    if not errors:
        print("%s: shader pack and Oculus config as expected" % argv[1])
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
