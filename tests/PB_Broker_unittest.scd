// Probe — Unit Test: ProbeBroker
// Verifies broker subscription deduplication, consumer reference counting, and cleanup.

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
	var broker = Probe.broker;
	var consumerA = \consumer_A;
	var consumerB = \consumer_B;
	var sourceA, sourceB;
	var receivedA = false, receivedB = false;

	"--- Testing ProbeBroker ---".postln;

	// Initially 0 sources
	assert.value(broker.sourceCount == 0, "Initial broker sourceCount is 0");

	// 1. Subscribe consumer A
	sourceA = broker.subscribeLevels(consumerA, s, \outputs, 0, 2, 15, { |peaks, rms|
		receivedA = true;
	});

	assert.value(sourceA.notNil, "Source A created");
	assert.value(broker.sourceCount == 1, "Broker sourceCount is 1 after first subscriber");
	assert.value(sourceA.consumerCount == 1, "Source A consumerCount is 1");
	assert.value(sourceA.isRunning, "Source A is running");

	// 2. Subscribe consumer B with IDENTICAL parameters
	sourceB = broker.subscribeLevels(consumerB, s, \outputs, 0, 2, 15, { |peaks, rms|
		receivedB = true;
	});

	assert.value(sourceB.notNil, "Source B created");
	assert.value(sourceA === sourceB, "Source A and Source B are EXACT SAME instance (identity ===)");
	assert.value(broker.sourceCount == 1, "Broker sourceCount REMAINS 1 (no duplicate source created)");
	assert.value(sourceA.consumerCount == 2, "Source A consumerCount is now 2");

	// 3. Unsubscribe consumer A
	broker.unsubscribe(consumerA, sourceA.key);

	assert.value(broker.sourceCount == 1, "Broker sourceCount remains 1 after consumer A unsubscribes");
	assert.value(sourceA.consumerCount == 1, "Source consumerCount dropped to 1");
	assert.value(sourceA.isRunning, "Source remains running for consumer B");

	// 4. Unsubscribe consumer B (last consumer)
	broker.unsubscribe(consumerB, sourceA.key);

	assert.value(broker.sourceCount == 0, "Broker sourceCount is now 0 after last consumer unsubscribes");
	assert.value(sourceA.isRunning.not, "Source is stopped and freed");
	assert.value(sourceA.consumerCount == 0, "Source consumerCount is 0");

	// Summary
	("--------------------------------------------------").postln;
	("ProbeBroker Tests: " ++ passed ++ " passed, " ++ failed ++ " failed.").postln;

	s.quit;
	if (failed == 0) {
		"ALL ProbeBroker TESTS PASSED!".postln;
		0.exit;
	} {
		"SOME TESTS FAILED!".postln;
		1.exit;
	};
});
)
