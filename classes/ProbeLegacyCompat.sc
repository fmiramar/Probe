/*
    ProbeLegacyCompat.sc
    Probe — Seamless Backward-Compatibility Layer for SCVisuals (v0.4.0)
    Allows legacy scripts and projects using SCV* classes to continue functioning
    identically by subclassing their unified Probe* and PB_* equivalents.
*/

SCVTheme : ProbeTheme {}
SCVMeasurementBroker : PB_Broker {}
SCVLevelsSource : PB_LevelsSource {}
SCVSpectrumSource : PB_SpectrumSource {}
SCVLoudnessSource : PB_LoudnessSource {}
SCVVectorscopeSource : PB_VectorscopeSource {}

SCVMeterView : PB_MeterView {}
SCVMeter : ProbeMeter {}

SCVScopeView : PB_ScopeView {}
SCVScope : ProbeScope {}
SCVSpectrumView : PB_SpectrumView {}
SCVSpectrum : ProbeSpectrum {}
SCVSpectrogramView : PB_SpectrogramView {}
SCVSpectrogram : ProbeSpectrogram {}
SCVWaterfallView : PB_WaterfallView {}
SCVWaterfall : ProbeWaterfall {}
SCVLoudnessView : PB_LoudnessView {}
SCVLoudness : ProbeLoudness {}
SCVVectorscopeView : PB_VectorscopeView {}
SCVVectorscope : ProbeVectorscope {}
SCVHistoryView : PB_HistoryView {}
SCVHistory : ProbeHistory {}
SCVDashboardView : PB_DashboardView {}
SCVDashboard : ProbeDashboard {}
