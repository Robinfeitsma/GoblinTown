package dev.goblintown.gfx;

import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan-overlay fixture headless acceptance (docs/10, DR-0001 validation 4
 * acceptance 1, rules/03 cat. 5–6): seeded synthetic graph, computed screen
 * clicks through the live transform, route append / truncate / reject asserted
 * on the observable fixture state. Pixel correctness + frame budget stay with
 * the on-GPU run (`--fixture overlay`).
 */
class PlanOverlayFixtureTest {

    private static HeadlessApplication app;

    /**
     * The headless runtime gives Gdx.files/app/input real (headless) values;
     * HeadlessGl swaps the NULL GL20 for the shim. The fixture is then driven
     * synchronously on the test thread — click/route logic is the thing under
     * test, not render-scheduling.
     */
    static void bootHeadlessRuntime() {
        app = new HeadlessApplication(new com.badlogic.gdx.ApplicationListener() {
            @Override public void create() { }
            @Override public void render() { }
            @Override public void resize(int width, int height) { }
            @Override public void pause() { }
            @Override public void resume() { }
            @Override public void dispose() { }
        }, new HeadlessApplicationConfiguration());
        HeadlessGl.install();
    }

    @AfterAll
    static void shutDown() {
        if (app != null) {
            app.exit();
        }
        HeadlessGl.restore();
    }

    @Test
    void clicksWalkTruncateAndRejectThroughTheLiveTransform() {
        bootHeadlessRuntime();
        PlanOverlayFixture fixture = new PlanOverlayFixture();
        try {
            fixture.create();
            for (int i = 0; i < 5; i++) {
                fixture.render(); // must not throw under the shim
            }

            IsoGrid iso = fixture.iso();
            DungeonGraphFixture dungeon = fixture.dungeon();

            // empty map click → rejected, harmless
            assertThat(fixture.click(-5000f, -5000f)).isFalse();
            assertThat(fixture.rejectedClicks()).isEqualTo(1);

            // start click with empty route: lands, changes nothing
            DungeonGraphFixture.Node start = dungeon.byId(dungeon.startId());
            assertThat(fixture.click(iso.screenX(start.col(), start.row()),
                    iso.screenY(start.col(), start.row()))).isTrue();
            assertThat(fixture.route()).isEmpty();
            assertThat(fixture.rejectedClicks()).isEqualTo(1);

            // append: click the start's first child → route = [child]
            DungeonGraphFixture.Node first = dungeon.byId(start.next().get(0));
            assertThat(fixture.click(iso.screenX(first.col(), first.row()),
                    iso.screenY(first.col(), first.row()))).isTrue();
            assertThat(fixture.route()).containsExactly(first.id());

            // rejection: a node that is NOT adjacent to the route tail —
            // the boss (last main node) can never be a tail-child here
            DungeonGraphFixture.Node boss = dungeon.byId(
                    dungeon.nodes().get(dungeon.nodes().size() - 1).id());
            if (!dungeon.isEdge(first.id(), boss.id())) {
                boolean accepted = fixture.click(iso.screenX(boss.col(), boss.row()),
                        iso.screenY(boss.col(), boss.row()));
                assertThat(accepted).as("boss is 2+ levels deep — must be rejected").isFalse();
                assertThat(fixture.rejectedClicks()).isEqualTo(2);
                assertThat(fixture.route()).containsExactly(first.id());
            }

            // walk one more level if the first node has children
            if (!first.next().isEmpty()) {
                DungeonGraphFixture.Node second = dungeon.byId(first.next().get(0));
                assertThat(fixture.click(iso.screenX(second.col(), second.row()),
                        iso.screenY(second.col(), second.row()))).isTrue();
                assertThat(fixture.route()).containsExactly(first.id(), second.id());
            }

            // truncation: re-click `first` → route truncates back to [first]
            if (fixture.route().size() > 1) {
                assertThat(fixture.click(iso.screenX(first.col(), first.row()),
                        iso.screenY(first.col(), first.row()))).isTrue();
                assertThat(fixture.route()).containsExactly(first.id());
                assertThat(fixture.truncations()).isEqualTo(1);
            }

            // start re-click clears the draw entirely
            assertThat(fixture.click(iso.screenX(start.col(), start.row()),
                    iso.screenY(start.col(), start.row()))).isTrue();
            assertThat(fixture.route()).isEmpty();
        } finally {
            fixture.dispose();
        }
    }

    @Test
    void fixtureIsDeterministicPerSeed() {
        // same seed → identical graph, twice (the fixture must not add
        // run-to-run variance to anything downstream)
        DungeonGraphFixture a = new DungeonGraphFixture(PlanOverlayFixture.SEED, 5);
        DungeonGraphFixture b = new DungeonGraphFixture(PlanOverlayFixture.SEED, 5);
        assertThat(a.nodes())
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(b.nodes());
        assertThat(new DungeonGraphFixture(PlanOverlayFixture.SEED + 1, 5).nodes())
                .isNotEqualTo(a.nodes());
    }
}
