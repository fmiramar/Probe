// SCVisuals — Unit Test: SCVLifecycle
// Verifies server lifecycle, CmdPeriod handling, and complete cleanup without orphan nodes.

(
s.waitForBoot({
	var passed = 0, failed = 0;
	var assert = { |cond, name|
		if (cond) {
			passed = passed + 1;
			("  [PASS] " ++ name).postln;
		} {
			failed = failed + 1;
			("  [FAIL] " ++ name).postln;
		};
	};
	var broker = SCVisuals.broker;
	var consumer = \lifecycle_consumer;
	var source;
	var nodeID1, nodeID2;

	"--- Testing SCVisuals Lifecycle & CmdPeriod ---".postln;

	// 1. Subscribe
	source = broker.subscribeLevels(consumer, s, \outputs, 0, 2, 15, { |peaks, rms| });
	0.5.wait;

	assert.value(source.notNil, "Source created");
	assert.value(source.isRunning, "Source is running");
	assert.value(source.synth.notNil, "Source synth is non-nil");
	nodeID1 = source.synth.nodeID;
	assert.value(nodeID1 > 0, "Source synth has valid server nodeID: " ++ nodeID1);

	// 2. Simulate CmdPeriod
	CmdPeriod.run;
	0.5.wait;

	assert.value(source.isRunning, "Source automatically restored after CmdPeriod");
	assert.value(source.synth.notNil, "Source has active synth after CmdPeriod");
	nodeID2 = source.synth.nodeID;
	assert.value(nodeID2 > 0, "Restored synth has valid nodeID: " ++ nodeID2);
	assert.value(nodeID1 != nodeID2, "Restored synth was cleanly recreated with new nodeID");

	// 3. Unsubscribe consumer
	broker.unsubscribe(consumer, source.key);
	0.5.wait;

	assert.value(source.synth.isNil, "Source synth is nil after unsubscription");
	assert.value(source.oscFunc.isNil, "Source oscFunc is nil after unsubscription");
	assert.value(source.isRunning.not, "Source is not running");
	assert.value(broker.sourceCount == 0, "Broker has 0 sources remaining");

	// 4. Test freeAll idempotency
	broker.freeAll;
	assert.value(broker.sourceCount == 0, "freeAll operates cleanly and idempotently");

	// Summary
	("--------------------------------------------------").postln;
	("SCVLifecycle Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL SCVLifecycle TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
