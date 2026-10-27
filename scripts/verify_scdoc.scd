// scripts/verify_scdoc.scd
(
var docsToVerify, hasError;

"Verifying SCDoc documentation entries for all 14 public instruments...".postln;
SCDoc.indexAllDocuments;

docsToVerify = [
	"Classes/Probe",
	"Classes/ProbeTheme",
	"Classes/ProbeScope",
	"Classes/ProbeSpectrum",
	"Classes/ProbeSpectrogram",
	"Classes/ProbeWaterfall",
	"Classes/ProbeMeter",
	"Classes/ProbeLoudness",
	"Classes/ProbeVectorscope",
	"Classes/ProbeBandAnalyzer",
	"Classes/ProbeSpectralDashboard",
	"Classes/ProbeDashboard",
	"Classes/ProbeInspector",
	"Classes/ProbeHistory",
	"Guides/Probe-Visuals-and-Representation-Suite"
];

hasError = false;

docsToVerify.do { |docPath|
	var entry = SCDoc.documents[docPath];
	if (entry.notNil) {
		("  [DOC FOUND]: " ++ docPath ++ " -> " ++ entry.title).postln;
		try {
			SCDoc.parseAndRender(entry);
			("    [PARSE OK]: " ++ docPath).postln;
		} { |err|
			("    [PARSE ERROR]: " ++ docPath ++ " -> " ++ err.errorString).warn;
			hasError = true;
		};
	} {
		("  [DOC MISSING]: " ++ docPath).postln;
		hasError = true;
	};
};

if (hasError) {
	"ERROR: One or more SCDoc documents failed verification!".postln;
	1.exit;
} {
	"All 15 Probe SCDoc documents verified and parsed successfully with zero errors!".postln;
	0.exit;
};
)
