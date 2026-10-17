// scripts/verify_scdoc.scd
"Verifying SCDoc documentation entries...".postln;
SCDoc.indexAllDocuments;
[
	"Classes/Probe",
	"Classes/SCVisuals",
	"Classes/SCVDashboard",
	"Classes/SCVLoudness",
	"Classes/SCVVectorscope",
	"Classes/SCVMeter",
	"Classes/SCVScope",
	"Classes/SCVSpectrum",
	"Classes/SCVSpectrogram",
	"Classes/SCVWaterfall",
	"Classes/SCVHistory"
].do { |docPath|
	var entry = SCDoc.documents[docPath];
	if (entry.notNil) {
		("  [DOC FOUND]: " ++ docPath ++ " -> " ++ entry.title).postln;
	} {
		("  [DOC MISSING]: " ++ docPath).postln;
	};
};
0.exit;
