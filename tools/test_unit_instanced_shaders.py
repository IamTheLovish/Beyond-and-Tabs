"""The instanced unit bodies' shaders compile (bar/src/main/resources/assets/reignofnether/shaders/core/bat_unit_instanced*).

The game compiles them at resource load; a typo there switches the path off for the session (InstancedBodies.fail),
which an eyes run would only report minutes later. glslangValidator (apt glslang-tools, which CI's tools job installs)
compiles each stage here in a second. Skipped where the tool is missing (a Windows laptop).
"""
import os
import shutil
import subprocess
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
CORE = os.path.join(HERE, '..', 'bar', 'src', 'main', 'resources', 'assets', 'reignofnether', 'shaders', 'core')
PROGRAMS = ('bat_unit_instanced', 'bat_unit_instanced_eyes')


@unittest.skipUnless(shutil.which('glslangValidator'), 'glslangValidator is not installed')
class UnitInstancedShadersCompile(unittest.TestCase):
    def compile(self, path, stage):
        run = subprocess.run(['glslangValidator', '-S', stage, path], capture_output=True, text=True)
        self.assertEqual(run.returncode, 0, f'{os.path.basename(path)} ({stage}) did not compile:\n{run.stdout}{run.stderr}')

    def test_every_stage_compiles(self):
        for name in PROGRAMS:
            self.compile(os.path.join(CORE, name + '.vsh'), 'vert')
            self.compile(os.path.join(CORE, name + '.fsh'), 'frag')


class UnitInstancedShaderFiles(unittest.TestCase):
    def test_each_program_has_both_stages_and_its_json(self):
        for name in PROGRAMS:
            for ext in ('.vsh', '.fsh', '.json'):
                self.assertTrue(os.path.isfile(os.path.join(CORE, name + ext)), name + ext + ' is missing')

    def test_body_and_light_pass_place_vertices_with_the_same_code(self):
        # the light pass meets its body's depth with LEQUAL: both programs must compute gl_Position identically
        def skin(name):
            src = open(os.path.join(CORE, name + '.vsh'), encoding='utf-8').read()
            lines = [ln.strip() for ln in src.splitlines()]
            keep = [ln for ln in lines if ln.startswith(('ivec4 head', 'int b =', 'vec4 at', 'vec3 pos', 'gl_Position'))]
            self.assertIn('invariant gl_Position;', lines, name + ' does not declare gl_Position invariant')
            return keep
        self.assertEqual(skin(PROGRAMS[0]), skin(PROGRAMS[1]))
        self.assertEqual(len(skin(PROGRAMS[0])), 5)


if __name__ == '__main__':
    unittest.main()
