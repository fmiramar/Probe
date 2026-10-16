// tests/run_all_tests.scd
// Master Test Runner for SCVisuals
// Discovers and executes all unit test suites, reporting aggregated pass/fail metrics.

(
var testFiles = [
	"tests/SCVTheme_unittest.scd",
	"tests/SCVBroker_unittest.scd",
	"tests/SCVLifecycle_unittest.scd",
	"tests/SCVMeter_unittest.scd",
	"tests/SCVScope_unittest.scd",
	"tests/SCVSpectrum_unittest.scd",
	"tests/SCVSpectrogram_unittest.scd",
	"tests/SCVLoudness_unittest.scd",
	"tests/SCVDashboard_unittest.scd"
];

var suitePassed = 0, suiteFailed = 0;
var basePath = "/Users/fmmac/Desktop/SC New Visulations servers/SCVisuals/";

"========================================================================".postln;
"             SCVisuals — Master Unit Test Suite Runner                  ".postln;
"========================================================================".postln;

testFiles.do { |relPath|
	var fullPath = basePath ++ relPath;
	var cmd = "sclang --include-path \"" ++ basePath ++ "classes\" \"" ++ fullPath ++ "\" > /tmp/scv_test_out.log 2>&1";
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
("SCVisuals Test Execution Summary: " ++ suitePassed ++ " suites passed, " ++ suiteFailed ++ " suites failed.").postln;
"========================================================================".postln;

if (suiteFailed == 0) {
	"ALL TEST SUITES PASSED CLEANLY!".postln;
	0.exit;
} {
	"ONE OR MORE TEST SUITES FAILED!".postln;
	1.exit;
};
)
