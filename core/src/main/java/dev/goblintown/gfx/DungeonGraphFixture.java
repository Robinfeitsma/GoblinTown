package dev.goblintown.gfx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * SYNTHETIC dungeon graph for the plan-overlay fixture (docs/10, OQ-1):
 * deterministic per seed, docs/04 node types. Fixture ≠ game truth — the
 * real run system (docs/04) will supply the plan and route data from the sim
 * ring; this class is throwaway by design and exists only to give validation
 * 4 a map to draw on.
 *
 * <p>Topology: a main chain of {@code depth + 1} levels — main node at level
 * {@code d} sits at grid cell {@code (d, d % 2)} (alternating rows, so cells
 * never collide). Each level {@code 1 ≤ d < depth} gets a SECOND zone
 * (cell {@code (d, d % 2 + 2)}) when the seeded coin fires: main → aux →
 * next main, so branch and main both rejoin exactly one level later. The
 * final main node is the boss. Identical seed → identical graph (H1 spirit:
 * the fixture must not be a source of run-to-run variance either).
 */
public final class DungeonGraphFixture {

    /** docs/04 zone types; visuals are tint+shape only in this phase. */
    public enum NodeType {
        ENCOUNTER, ROOM, SHRINE, BEACON, BOSS
    }

    /** One zone: integer grid cell + graph edges (children only). */
    public record Node(String id, int col, int row, NodeType type, List<String> next) {
        public Node {
            next = List.copyOf(next);
        }
    }

    public static final int DEFAULT_DEPTH = 5;

    private final Map<String, Node> byId = new LinkedHashMap<>();
    private final List<Node> nodes = new ArrayList<>();
    private final String startId;

    public DungeonGraphFixture(long seed, int depth) {
        if (depth < 2) {
            throw new IllegalArgumentException("depth must be ≥ 2");
        }
        Random rnd = new Random(seed);
        Node prevMain = null;
        String prevAux = null;
        for (int d = 0; d <= depth; d++) {
            Node node = add(new Node(id(), d, d % 2,
                    d == depth ? NodeType.BOSS : pickType(rnd), new ArrayList<>()));

            if (prevAux != null) {
                connect(prevAux, List.of(node.id()));
            }
            if (prevMain != null) {
                List<String> toMain = new ArrayList<>(prevMain.next());
                toMain.add(node.id());
                connect(prevMain.id(), toMain);
            }

            prevAux = null;
            if (d >= 1 && d < depth && rnd.nextBoolean()) {
                String auxId = add(new Node(id(), d, (d % 2) + 2,
                        pickType(rnd), new ArrayList<>())).id();
                connect(node.id(), List.of(auxId));
                prevAux = auxId;
            }
            prevMain = node;
        }
        this.startId = nodes.get(0).id();
    }

    // -- construction ---------------------------------------------------

    private Node add(Node node) {
        byId.put(node.id(), node);
        nodes.add(node);
        return node;
    }

    private void connect(String parentId, List<String> children) {
        Node parent = byId.get(parentId);
        Node updated = new Node(parent.id(), parent.col(), parent.row(),
                parent.type(), children);
        nodes.set(nodes.indexOf(parent), updated);
        byId.put(parentId, updated);
    }

    private String id() {
        return "zone-%03d".formatted(nodes.size());
    }

    private static NodeType pickType(Random rnd) {
        NodeType[] pool = {NodeType.ENCOUNTER, NodeType.ROOM, NodeType.SHRINE,
                NodeType.BEACON};
        return pool[rnd.nextInt(pool.length)];
    }

    // -- queries --------------------------------------------------------

    public List<Node> nodes() {
        return List.copyOf(nodes);
    }

    public Node byId(String id) {
        Node node = byId.get(id);
        if (node == null) {
            throw new IllegalArgumentException("no such node: " + id);
        }
        return node;
    }

    public String startId() {
        return startId;
    }

    /** True iff {@code to} is an outgoing graph edge of {@code from}. */
    public boolean isEdge(String from, String to) {
        return byId(from).next().contains(to);
    }

    public boolean hasNodeAt(int col, int row) {
        return nodeAt(col, row) != null;
    }

    public Node nodeAt(int col, int row) {
        for (Node node : nodes) {
            if (node.col() == col && node.row() == row) {
                return node;
            }
        }
        return null;
    }
}
