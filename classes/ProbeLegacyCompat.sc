/*
    ProbeLegacyCompat.sc
    Probe — Seamless Backward-Compatibility Layer for SCVisuals (v0.3.0)
    Allows legacy scripts and projects using SCV* classes to continue functioning
    identically by subclassing their unified Probe* equivalents.
*/

SCVTheme : ProbeTheme {}
SCVMeasurementBroker : ProbeBroker {}
SCVLevelsSource : ProbeLevelsSource {}
SCVSpectrumSource : ProbeSpectrumSource {}
SCVLoudnessSource : ProbeLoudnessSource {}
SCVVectorscopeSource : ProbeVectorscopeSource {}

SCVMeterView : ProbeMeterView {}
SCVMeter : ProbeMeter {}

SCVScopeView : ProbeScopeView {}
SCVScope : ProbeScope {}
SCVSpectrumView : ProbeSpectrumView {}
SCVSpectrum : ProbeSpectrum {}
SCVSpectrogramView : ProbeSpectrogramView {}
SCVSpectrogram : ProbeSpectrogram {}
SCVWaterfallView : ProbeWaterfallView {}
SCVWaterfall : ProbeWaterfall {}
SCVLoudnessView : ProbeLoudnessView {}
SCVLoudness : ProbeLoudness {}
SCVVectorscopeView : ProbeVectorscopeView {}
SCVVectorscope : ProbeVectorscope {}
SCVHistoryView : ProbeHistoryView {}
SCVHistory : ProbeHistory {}
SCVDashboardView : ProbeDashboardView {}
SCVDashboard : ProbeDashboard {}
