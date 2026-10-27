"""Pure unit checks; never invokes Docker, Git, Postgres or Java."""
import importlib.util
import unittest
from pathlib import Path
from unittest.mock import patch

module_spec = importlib.util.spec_from_file_location(
    'olp_e2e_preflight', Path(__file__).with_name('olp_e2e_preflight.py')
)
preflight = importlib.util.module_from_spec(module_spec)
module_spec.loader.exec_module(preflight)


def fixture(**overrides):
    sample = {
        'Name': '/dxos-stage6-pg17-disposable',
        'State': {'Running': True},
        'Config': {'Image': 'postgres:17.11-alpine', 'Env': [
            'POSTGRES_USER=stage6_test', 'POSTGRES_DB=stage6_disposable_test',
            'POSTGRES_PASSWORD=secret_not_printed',
        ]},
        'NetworkSettings': {'Ports': {'5432/tcp': [{
            'HostIp': '127.0.0.1', 'HostPort': '55427',
        }]}},
        'Mounts': [],
    }
    sample.update(overrides)
    return [sample]


class Stage6PreflightTest(unittest.TestCase):
    def assert_rejected(self, value):
        with self.assertRaises(preflight.PreflightBlocked):
            preflight.validate_docker_inspect(value)

    def test_pinned_disposable_passes_metadata(self):
        preflight.validate_docker_inspect(fixture())

    def test_wrong_name_rejected(self):
        self.assert_rejected(fixture(Name='/production-db'))

    def test_stopped_container_rejected(self):
        self.assert_rejected(fixture(State={'Running': False}))

    def test_postgres18_rejected(self):
        self.assert_rejected(fixture(Config={'Image': 'postgres:18.6-alpine', 'Env': [
            'POSTGRES_USER=stage6_test', 'POSTGRES_DB=stage6_disposable_test']}))

    def test_wrong_database_rejected(self):
        self.assert_rejected(fixture(Config={'Image': 'postgres:17', 'Env': [
            'POSTGRES_USER=stage6_test', 'POSTGRES_DB=postgres']}))

    def test_exposed_port_rejected(self):
        self.assert_rejected(fixture(NetworkSettings={'Ports': {'5432/tcp': [{
            'HostIp': '0.0.0.0', 'HostPort': '55427'}]}}))

    def test_wrong_port_rejected(self):
        self.assert_rejected(fixture(NetworkSettings={'Ports': {'5432/tcp': [{
            'HostIp': '127.0.0.1', 'HostPort': '5432'}]}}))

    def test_mounted_data_rejected(self):
        self.assert_rejected(fixture(Mounts=[{'Type': 'bind', 'Source': 'C:\\data'}]))

    @patch.object(preflight, 'command')
    def test_dirty_git_checkout_rejected(self, command):
        sha = 'a' * 40
        command.side_effect = [sha, ' M important.sql']
        with self.assertRaises(preflight.PreflightBlocked):
            preflight.check_checkout(Path('.'), sha)

    @patch.object(preflight, 'command')
    def test_wrong_git_head_rejected(self, command):
        command.return_value = 'b' * 40
        with self.assertRaises(preflight.PreflightBlocked):
            preflight.check_checkout(Path('.'), 'a' * 40)

    @patch.object(preflight, 'command')
    def test_matching_clean_git_head(self, command):
        sha = 'a' * 40
        command.side_effect = [sha, '']
        preflight.check_checkout(Path('.'), sha)
        self.assertEqual(command.call_count, 2)

    @patch.object(preflight, 'command')
    def test_read_only_psql_queries(self, command):
        import json
        command.side_effect = [json.dumps(fixture()), '170011',
                               'stage6_disposable_test']
        preflight.check_postgres17()
        args = [c.args[0] for c in command.call_args_list]
        self.assertEqual(args[0][:2], ['docker', 'inspect'])
        self.assertEqual(args[1][-2:], ['-c', 'SHOW server_version_num'])
        self.assertEqual(args[2][-2:], ['-c', 'SELECT current_database()'])

    @patch.object(preflight, 'command')
    def test_pg18_server_rejected_even_if_image_pg17(self, command):
        import json
        command.side_effect = [json.dumps(fixture()), '180006']
        with self.assertRaises(preflight.PreflightBlocked):
            preflight.check_postgres17()


if __name__ == '__main__':
    unittest.main()
