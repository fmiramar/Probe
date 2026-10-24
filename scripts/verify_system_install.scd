// scripts/verify_system_install.scd
// Verifies system-wide installation of Probe (v0.4.0) with Dual-Tier namespace and *Pb ergonomics.

(
var passed = 0, failed = 0;
var assert = { |cond, msg|
	if (cond) {
		passed = passed + 1;
		("  [PASS] " ++ msg).postln;
	} {
		failed = failed + 1;
		("  [FAIL] " ++ msg).postln;
	};
};

"========================================================================".postln;
"Testing System-Wide SuperCollider Installation of Probe (v0.4.0)...".postln;
"========================================================================".postln;

// 1. Probe Public User-Facing Instruments (Tier 1 — Exactly 14 Classes)
[
	Probe,
	ProbeTheme,
	ProbeScope,
	ProbeSpectrum,
	ProbeSpectrogram,
	ProbeWaterfall,
	ProbeMeter,
	ProbeLoudness,
	ProbeVectorscope,
	ProbeBandAnalyzer,
	ProbeSpectralDashboard,
	ProbeDashboard,
	ProbeInspector,
	ProbeHistory
].do { |cls|
	assert.value(cls.notNil, "Public instrument class available: " ++ cls.asString);
};

// Autocomplete cleanliness check: exactly 14 classes begin with "Probe"
assert.value(
	Class.allClasses.collect(_.name).select({ |n| n.asString.beginsWith("Probe") }).size == 14,
	"IDE autocomplete cleanly restricted to exactly 14 Probe* classes"
);

// 2. Internal Plumbing Classes (Tier 2 — PB_*)
[
	PB_Broker,
	PB_LevelsSource,
	PB_SpectrumSource,
	PB_LoudnessSource,
	PB_VectorscopeSource,
	PB_SpectralSource,
	PB_ScopeView,
	PB_SpectrumView,
	PB_SpectrogramView,
	PB_WaterfallView,
	PB_MeterView,
	PB_LoudnessView,
	PB_VectorscopeView,
	PB_HistoryView,
	PB_DashboardView,
	PB_BandAnalyzerView,
	PB_SpectralDashboardView,
	PB_GraphView,
	PB_DescriptorsView,
	PB_PitchTrackerView,
	PB_ChromagramView,
	PB_SpectralCompareView,
	PB_Trace,
	PB_TraceScopeWindow,
	PB_TraceHistoryWindow,
	PB_TraceMeterWindow,
	PB_TraceSpectrumWindow
].do { |cls|
	assert.value(cls.notNil, "Internal plumbing class available: " ++ cls.asString);
};

// 3. SCVisuals Backwards-Compatible Classes
[
	SCVisuals,
	SCVTheme,
	SCVMeasurementBroker,
	SCVLevelsSource,
	SCVSpectrumSource,
	SCVLoudnessSource,
	SCVVectorscopeSource,
	SCVMeter,
	SCVMeterView,
	SCVScope,
	SCVScopeView,
	SCVSpectrum,
	SCVSpectrumView,
	SCVSpectrogram,
	SCVSpectrogramView,
	SCVWaterfall,
	SCVWaterfallView,
	SCVHistory,
	SCVHistoryView,
	SCVLoudness,
	SCVLoudnessView,
	SCVVectorscope,
	SCVVectorscopeView,
	SCVDashboard,
	SCVDashboardView
].do { |cls|
	assert.value(cls.notNil, "Backwards-compatible class available: " ++ cls.asString);
};

// 4. Server *Pb Unified Ergonomic Methods
assert.value(Server.findMethod(\meterPb).notNil, "Server:meterPb extension registered");
assert.value(Server.findMethod(\scopePb).notNil, "Server:scopePb extension registered");
assert.value(Server.findMethod(\freqscopePb).notNil, "Server:freqscopePb extension registered");
assert.value(Server.findMethod(\spectrumPb).notNil, "Server:spectrumPb extension registered");
assert.value(Server.findMethod(\spectrogramPb).notNil, "Server:spectrogramPb extension registered");
assert.value(Server.findMethod(\waterfallPb).notNil, "Server:waterfallPb extension registered");
assert.value(Server.findMethod(\historyPb).notNil, "Server:historyPb extension registered");
assert.value(Server.findMethod(\loudnessPb).notNil, "Server:loudnessPb extension registered");
assert.value(Server.findMethod(\vectorscopePb).notNil, "Server:vectorscopePb extension registered");
assert.value(Server.findMethod(\dashboardPb).notNil, "Server:dashboardPb extension registered");
assert.value(Server.findMethod(\spectralDashboardPb).notNil, "Server:spectralDashboardPb extension registered");
assert.value(Server.findMethod(\bandAnalyzerPb).notNil, "Server:bandAnalyzerPb extension registered");
assert.value(Server.findMethod(\inspectorPb).notNil, "Server:inspectorPb extension registered");
assert.value(Server.findMethod(\inspectPb).notNil, "Server:inspectPb extension registered");

// 5. Server Legacy Forwarders
assert.value(Server.findMethod(\meter2).notNil, "Server:meter2 legacy forwarder registered");
assert.value(Server.findMethod(\scope2).notNil, "Server:scope2 legacy forwarder registered");
assert.value(Server.findMethod(\freqscope2).notNil, "Server:freqscope2 legacy forwarder registered");

// 6. View Factory Methods on Public Instruments
[
	ProbeScope,
	ProbeSpectrum,
	ProbeSpectrogram,
	ProbeWaterfall,
	ProbeMeter,
	ProbeLoudness,
	ProbeVectorscope,
	ProbeHistory,
	ProbeDashboard,
	ProbeBandAnalyzer,
	ProbeSpectralDashboard,
	ProbeInspector
].do { |cls|
	assert.value(cls.class.findMethod(\view).notNil, cls.asString ++ ".view factory method registered");
};

// 7. In-Graph Tracing
assert.value(UGen.findMethod(\trace).notNil, "UGen:trace extension registered");

// 8. Versioning & Theme Integrity
assert.value(Probe.version == "0.4.0", "Probe.version is 0.4.0");
assert.value(SCVisuals.version == "0.4.0", "SCVisuals.version is 0.4.0");
assert.value(Probe.theme.name === \graphite, "Probe default theme is \\graphite");
assert.value(Probe.fontScale == 1.25, "Probe default fontScale is 1.25");

"========================================================================".postln;
("System Verification Results: " ++ passed ++ " Passed, " ++ failed ++ " Failed.").postln;
"========================================================================".postln;

if (failed == 0) {
	"SYSTEM-WIDE INSTALLATION VERIFIED SUCCESSFULLY!".postln;
	0.exit;
} {
	"SYSTEM-WIDE INSTALLATION VERIFICATION FAILED!".postln;
	1.exit;
};
)
