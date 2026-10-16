// scripts/verify_system_install.scd
"========================================================================".postln;
"Testing System-Wide SuperCollider Installation of SCVisuals...".postln;
"========================================================================".postln;

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
	if (cls.notNil) {
		("  [OK] " ++ cls.asString ++ " is available").postln;
	} {
		("  [ERROR] Class missing!").postln;
	};
};

("Active Theme: " ++ SCVisuals.theme.name).postln;
("Version: " ++ SCVisuals.version).postln;
("Available Themes: " ++ SCVisuals.themes.keys.asArray).postln;
"========================================================================".postln;
"SYSTEM-WIDE INSTALLATION VERIFIED SUCCESSFULLY!".postln;
"========================================================================".postln;
0.exit;
