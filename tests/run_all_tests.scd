// tests/run_all_tests.scd
// Master Test Runner for Probe Suite
// Discovers and executes all unit test suites, reporting aggregated pass/fail metrics.

(
var testFiles = [
	"tests/ProbeTheme_unittest.scd",
	"tests/PB_Broker_unittest.scd",
	"tests/ProbeLifecycle_unittest.scd",
	"tests/ProbeMeter_unittest.scd",
	"tests/ProbeScope_unittest.scd",
	"tests/ProbeSpectrum_unittest.scd",
	"tests/ProbeSpectrogram_unittest.scd",
	"tests/ProbeLoudness_unittest.scd",
	"tests/ProbeDashboard_unittest.scd",
	"tests/Probe_P1_unittest.scd",
	"tests/Probe_P2_unittest.scd",
	"tests/Probe_P3_unittest.scd",
	"tests/Probe_P4_unittest.scd",
	"tests/Probe_P5_unittest.scd"
];

var suitePassed = 0, suiteFailed = 0;
var basePath = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/";

"========================================================================".postln;
"             Probe — Master Unit Test Suite Runner (v0.4.0)             ".postln;
"========================================================================".postln;

testFiles.do { |relPath|
	var fullPath = basePath ++ relPath;
	var cmd = "sclang -l \"" ++ basePath ++ "sclang_conf.yaml\" \"" ++ fullPath ++ "\" > /tmp/probe_test_out.log 2>&1; sleep 0.4";
	var exitCode;

	("--> Running Suite: " ++ relPath).postln;
	exitCode = cmd.systemCmd;

	if (exitCode == 0) {
		("    [SUITE PASSED]: " ++ relPath).postln;
		suitePassed = suitePassed + 1;
	} {
		("    [SUITE FAILED (exit " ++ exitCode ++ ")]: " ++ relPath).postln;
		suiteFailed = suiteFailed + 1;
	};
};

"========================================================================".postln;
("Probe Test Execution Summary: " ++ suitePassed ++ " suites passed, " ++ suiteFailed ++ " suites failed.").postln;
"========================================================================".postln;

if (suiteFailed == 0) {
	"ALL TEST SUITES PASSED CLEANLY!".postln;
	0.exit;
} {
	"ONE OR MORE TEST SUITES FAILED!".postln;
	1.exit;
};
)
