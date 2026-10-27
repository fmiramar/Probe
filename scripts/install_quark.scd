// scripts/install_quark.scd
var qPath = "/Users/fmmac/Desktop/SC New Visulations servers/Probe";
"Installing Probe Quark...".postln;
Quarks.install(qPath);
"Quark installed into SuperCollider configuration.".postln;
"Active Quarks: ".post;
Quarks.installed.do { |q| (q.name ++ " (" ++ q.version ++ ") ").post };
"".postln;
0.exit;
