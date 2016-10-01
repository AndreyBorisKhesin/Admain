import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;


public class PlayerAI {
    boolean statsSet;
    int numberOfControlPoints;
    int numberOfMainframes;
    int worldHeight;
    int worldWidth;
    int maximumEffectiveRange;
    // int averageEffectiveRange;

    public PlayerAI() {
        statsSet = false;
    }

    private int enemyNumber(Team ours, Team other) {
        if (other == Team.NONE) {
            return 0;
        }
        if (ours == Team.BLUE && other == Team.BLUE) {
            return 1;
        }
        if (ours == Team.AMBER && other == Team.AMBER) {
            return 1;
        }
        return -1;
    }

    private ControlPoint findPriorityControlPoint(
            World world,
            Point p,
            Team friendly,
            double mainframeMultiplierCost,
            double friendlyMultiplierCost,
            double enemyMultiplierCost
            ) {
        /**
         * Breaks if no control points on map.
         */
        ControlPoint[] allPoints = world.getControlPoints();
        ControlPoint closest = null;
        double longest = Double.NaN;
        if (mainframeMultiplierCost != mainframeMultiplierCost) {
            mainframeMultiplierCost = 1.0;
        }
        if (friendlyMultiplierCost != friendlyMultiplierCost) {
            friendlyMultiplierCost = 1.0;
        }
        if (enemyMultiplierCost != enemyMultiplierCost) {
            enemyMultiplierCost = 1.0;
        }
        for (ControlPoint cp: allPoints) {
            double len = world.getPathLength(p, cp.getPosition());
            if (cp.isMainframe()) {
                len *= mainframeMultiplierCost;
            }
            if (enemyNumber(friendly, cp.getControllingTeam()) == -1) {
                len *= enemyMultiplierCost;
            }
            if (enemyNumber(friendly, cp.getControllingTeam()) == 1) {
                len *= friendlyMultiplierCost;
            }
            if (longest != longest || len < longest) {
                longest = len;
                closest = cp;
            }
        }
        return closest;
    }

    private void setStats(
            World world,
            EnemyUnit[] enemyUnits,
            FriendlyUnit[] friendlyUnits) {
        ControlPoint[] points = world.getControlPoints();
        this.numberOfControlPoints = points.length;
        this.numberOfMainframes = 0;
        for (ControlPoint p: points) {
            if (p.isMainframe()) {
                this.numberOfMainframes++;
            }
        }

        System.out.print("Mainframe amount: ");
        System.out.println(this.numberOfMainframes);

        Point p = Point.origin();
        this.worldHeight = -1;
        while (world.isWithinBounds(p)) {
            p = p.add(new Point(0,1));
            this.worldWidth++;
        }
        p = Point.origin();
        this.worldHeight = -1;
        while (world.isWithinBounds(p)) {
            p = p.add(new Point(1,0));
            this.worldHeight++;
        }

        System.out.print("Width:");
        System.out.println(this.worldWidth);

        System.out.print("Height: ");
        System.out.println(this.worldHeight);

        this.maximumEffectiveRange = 0;
        for (int x = 0; x < this.worldWidth; x++) {
            for (int y = 0; y < this.worldHeight; y++) {
                Point start = new Point(x, y);
                if (world.getTile(start) == TileType.WALL) {
                    continue;
                }
                for (Direction d: Direction.values()) {
                    if (d == Direction.NOWHERE) {
                        continue;
                    }
                    int r = 0;
                    Point target = d.movePoint(start);
                    while (world.canShooterShootTarget(start, target, 10)) {
                        target = d.movePoint(target);
                        r++;
                    }
                    if (r > this.maximumEffectiveRange) {
                        this.maximumEffectiveRange = r;
                    }
                    if (this.maximumEffectiveRange == 10) {
                        break;
                    }
                }
                if (this.maximumEffectiveRange == 10) {
                    break;
                }
            }
            if (this.maximumEffectiveRange == 10) {
                break;
            }
        }
        System.out.print("Maximum Effective Range: ");
        System.out.println(this.maximumEffectiveRange);
        this.statsSet = true;
    }

    /**
     * This method will get called every turn.
     *
     * @param world The latest state of the world.
     * @param enemyUnits An array of all 4 units on the enemy team.
     *  Their order won't change.
     * @param friendlyUnits An array of all 4 units on your team.
     *  Their order won't change.
     */
    public void doMove(
            World world,
            EnemyUnit[] enemyUnits,
            FriendlyUnit[] friendlyUnits) {
        if (!this.statsSet) {
            this.setStats(world, enemyUnits, friendlyUnits);
        }
        for (FriendlyUnit f: friendlyUnits) {
            f.move(world.getNextDirectionInPath(
                f.getPosition(),
                findPriorityControlPoint(
                    world,
                    f.getPosition(),
                    f.getTeam(),
                    0.3,
                    5,
                    0.5).getPosition()));
        }
    }
}
