/**
 * The entire deterministic simulation lives under this package tree.
 *
 * Architecture rules (docs/rules/02-coding.md M1–M6):
 *   - no libGDX / engine imports, ever (compiler-enforced: :sim has no gdx
 *     dependency);
 *   - int64 fixed-point arithmetic only — never float/double (H1);
 *   - ticks are the only time; wall-clock never enters this ring;
 *   - all cross-boundary traffic is serializable: commands in,
 *     state snapshots + timeline events out (M3).
 */
package dev.goblintown.sim;
