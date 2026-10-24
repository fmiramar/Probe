// tests/ProbeSpectrogram_unittest.scd
// Unit tests for ProbeSpectrogram, ProbeWaterfall, and ProbeHistory

var passCount = 0, failCount = 0;
var assert = { |cond, desc|
    if (cond) {
        ("PASS: " ++ desc).postln;
        passCount = passCount + 1;
    } {
        ("FAIL: " ++ desc).postln;
        failCount = failCount + 1;
    };
};

"=== Starting Probe P5 (Spectrogram / Waterfall / History) Unit Tests ===".postln;

try {
    var win, specView, waterView, histView, sourceKey;
    var server = Server(\testP5, NetAddr("127.0.0.1", 57125));
    var broker = Probe.broker;

    win = Window("Test P5", Rect(0, 0, 800, 600));

    // 1. Spectrogram View initialization
    specView = PB_SpectrogramView(server, win, Rect(0, 0, 300, 200), 0, 64, \graphite, 20);
    assert.value(specView.notNil, "PB_SpectrogramView instantiates");
    assert.value(specView.numBands == 64, "PB_SpectrogramView has 64 bands");
    assert.value(specView.img.notNil, "PB_SpectrogramView preallocated Image buffer");
    assert.value(specView.colorLUT.size == 256, "PB_SpectrogramView builds 256-entry color LUT");
    assert.value(specView.isFrozen == false, "PB_SpectrogramView starts unfrozen");

    specView.toggleFreeze;
    assert.value(specView.isFrozen == true, "toggleFreeze toggles freeze state");
    specView.toggleFreeze;

    // 2. Waterfall View initialization
    waterView = PB_WaterfallView(server, win, Rect(310, 0, 300, 200), 0, 64, \graphite, 20, 32);
    assert.value(waterView.notNil, "PB_WaterfallView instantiates");
    assert.value(waterView.numBands == 64, "PB_WaterfallView has 64 bands");
    assert.value(waterView.numLines == 32, "PB_WaterfallView has 32 lines");
    assert.value(waterView.isFrozen == false, "PB_WaterfallView starts unfrozen");

    // 3. Broker Spectral Source Sharing Check
    assert.value(specView.source === waterView.source, "Spectrogram and Waterfall on same bus share identical PB_SpectrumSource instance");
    assert.value(specView.source.consumerCount == 2, "Shared PB_SpectrumSource reports consumerCount == 2");

    // 4. Feed mock spectrum data
    specView.prHandleBands(FloatArray.fill(64, { |i| (i / 64) * 0.5 }));
    waterView.prHandleBands(FloatArray.fill(64, { |i| (i / 64) * 0.5 }));
    assert.value(true, "Spectrogram and Waterfall handle incoming band frames without error");

    // 5. History View initialization
    histView = PB_HistoryView(server, win, Rect(0, 220, 400, 200), \outputs, 0, 2, \graphite, 20, 100);
    assert.value(histView.notNil, "PB_HistoryView instantiates");
    assert.value(histView.numChannels == 2, "PB_HistoryView has 2 channels");
    assert.value(histView.maxPoints == 100, "PB_HistoryView allocates maxPoints 100");
    assert.value(histView.source.notNil, "PB_HistoryView subscribes to PB_LevelsSource");

    // Feed mock levels data
    histView.prHandleLevels(FloatArray[0.5, 0.7], FloatArray[0.2, 0.3]);
    assert.value(true, "ProbeHistoryView handles incoming peak/RMS frames without error");

    // 6. Theme Switching
    specView.theme = \ice;
    waterView.theme = \ice;
    histView.theme = \ice;
    assert.value(specView.theme.name == \ice, "Spectrogram updates theme dynamically");
    assert.value(waterView.theme.name == \ice, "Waterfall updates theme dynamically");
    assert.value(histView.theme.name == \ice, "History updates theme dynamically");

    // 7. Cleanup & Unsubscribe
    specView.stop;
    assert.value(specView.isClosed == true, "Spectrogram view stop marks closed");
    assert.value(waterView.source.consumerCount == 1, "Broker consumerCount decrements to 1 after Spectrogram unsubscription");

    sourceKey = waterView.source.key;
    waterView.stop;
    assert.value(broker.sources[sourceKey].isNil, "Broker source freed and cleaned up when consumerCount reaches 0");

    histView.stop;
    assert.value(histView.isClosed == true, "History view stop marks closed");

    win.close;
    assert.value(true, "Window closes cleanly");

} { |err|
    ("EXCEPTION in unit test: " ++ err.errorString).postln;
    err.dumpBackTrace;
    failCount = failCount + 1;
};

("=== P5 Unit Tests Completed: " ++ passCount ++ " passed, " ++ failCount ++ " failed ===").postln;

if (failCount == 0) {
    0.exit;
} {
    1.exit;
};
