// scripts/verify_scdoc.scd
"Verifying SCDoc documentation entries...".postln;
SCDoc.indexAllDocuments;
[
	"Classes/Probe",
	"Classes/ProbeDashboard",
	"Classes/ProbeHistory",
	"Classes/ProbeLevelsSource",
	"Classes/ProbeLoudness",
	"Classes/ProbeBroker",
	"Classes/ProbeMeter",
	"Classes/ProbeScope",
	"Classes/ProbeSpectrogram",
	"Classes/ProbeSpectrum",
	"Classes/ProbeTheme",
	"Classes/ProbeVectorscope",
	"Classes/ProbeWaterfall",
	"Classes/SCVisuals"
].do { |docPath|
	var entry = SCDoc.documents[docPath];
	if (entry.notNil) {
		("  [DOC FOUND]: " ++ docPath ++ " -> " ++ entry.title).postln;
	} {
		("  [DOC MISSING]: " ++ docPath).postln;
		1.exit;
	};
};
"All SCDoc documents verified successfully!".postln;
0.exit;
