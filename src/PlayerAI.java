import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;

import java.util.ArrayList;

public class PlayerAI {
    static final Direction[] directions = {Direction.EAST, Direction.NORTH,
            Direction.NORTH_EAST, Direction.NORTH_WEST, Direction.SOUTH,
            Direction.SOUTH_EAST, Direction.SOUTH_WEST, Direction.WEST};

    boolean statsSet;
    int numberOfControlPoints;
    int numberOfMainframes;
    int worldHeight;
    int worldWidth;
    int maximumEffectiveRange;
    // int averageEffectiveRange;
    //

    double fudgeFactor = 1.01;

    public PlayerAI() {
        statsSet = false;
    }

    private ArrayList<Point> path(World world, EnemyUnit[] enemyUnits, Point start,
                                  Point end) {
        double[][] distances = new double[worldWidth][worldHeight];
        boolean[][] visited = new boolean[worldWidth][worldHeight];
        ArrayList<Point>[][] paths =
                new ArrayList<Point>[worldWidth][worldHeight];
        for (int i = 0; i < distances.length; i++) {
            for (int j = 0; j < distances[i].length; j++) {
                paths[i][j] = new ArrayList<>();
                distances[i][j] = i == start.getX() && j == start.getY() ? 0 :
                        Double.MAX_VALUE;
                if (i == start.getX() && j == start.getY()) {
                    paths[i][j].add(start);
                }
            }
        }
        Point current = start;
        while (!visited[end.getX()][end.getY()]) {
            visited[current.getX()][current.getY()] = true;
            for (Direction direction : directions) {
                Point considered = direction.movePoint(current);
                if (
                        world.isWithinBounds(considered) &&
                        world.getTile(considered) != TileType.WALL &&
                        !visited[considered.getX()][considered.getY()]) {
                    double newDistance =
                            distances[current.getX()][current.getY()] + 1;
                    //node weight of considered goes instead of the 1 above
                    if (distances[considered.getX()][considered.getY()] >
                            newDistance) {
                        distances[considered.getX()][considered.getY()] =
                                newDistance;
                        paths[considered.getX()][considered.getY()] =
                                (ArrayList<Point>)
                                        paths[current.getX()][current.getY()]
                                                .clone();
                        paths[considered.getX()][considered.getY()]
                                .add(considered);
                    }
                }
            }
            double minDistance = Double.MAX_VALUE;
            Point next = null;
            for (int i = 0; i < distances.length; i++) {
                for (int j = 0; j < distances[i].length; j++) {
                    if (!visited[i][j]) {
                        if (distances[i][j] < minDistance) {
                            minDistance = distances[i][j];
                            next = new Point(i, j);
                        }
                    }
                }
            }
            current = next;
        }
        return paths[end.getX()][end.getY()];
    }

    private int totalDistance (World world, Point... ps) {
        int total = 0;
        for (Point p1 : ps) {
            for (Point p2 : ps) {
                total += world.getPathLength(p1, p2);
            }
        }
        return total/2;
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

    private int supNormFast(Point x, Point y) {
        Point d = x.subtract(y);
        return Math.max(d.signum().getX()*x.getX(),d.signum().getY()*y.getY());
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

    private boolean isGun(Pickup p) {
        if (p.getPickupType() == PickupType.REPAIR_KIT) {
            return false;
        }
        if (p.getPickupType() == PickupType.SHIELD) {
            return false;
        }
        return true;
    }

    private WeaponType pickToGun (Pickup p) {
        if (p.getPickupType() == PickupType.WEAPON_LASER_RIFLE) {
            return WeaponType.LASER_RIFLE;
        }
        if (p.getPickupType() == PickupType.WEAPON_MINI_BLASTER) {
            return WeaponType.MINI_BLASTER;
        }
        if (p.getPickupType() == PickupType.WEAPON_SCATTER_GUN) {
            return WeaponType.SCATTER_GUN;
        }
        if (p.getPickupType() == PickupType.WEAPON_RAIL_GUN) {
            return WeaponType.RAIL_GUN;
        }
        return null;
    }

    private PickupType gunToPick (WeaponType w) {
        if (w == WeaponType.LASER_RIFLE) {
            return PickupType.WEAPON_LASER_RIFLE;
        }
        if (w == WeaponType.MINI_BLASTER) {
            return PickupType.WEAPON_MINI_BLASTER;
        }
        if (w == WeaponType.SCATTER_GUN) {
            return PickupType.WEAPON_SCATTER_GUN;
        }
        if (w == WeaponType.RAIL_GUN) {
            return PickupType.WEAPON_RAIL_GUN;
        }
        return null;
    }

    private int pickupScore (PickupType p) {
        if (p == PickupType.REPAIR_KIT) {
            return 40;
        }
        if (p == PickupType.SHIELD) {
            return 0;
        }
        if (p == PickupType.WEAPON_LASER_RIFLE) {
            return 8*Math.min(4, this.maximumEffectiveRange);
        }
        if (p == PickupType.WEAPON_MINI_BLASTER) {
            return 4*Math.min(5, this.maximumEffectiveRange);
        }
        if (p == PickupType.WEAPON_SCATTER_GUN) {
            return 25*Math.min(2, this.maximumEffectiveRange);
        }
        if (p == PickupType.WEAPON_RAIL_GUN) {
            return 6*Math.min(10, this.maximumEffectiveRange);
        }
        return -128;
    }

    private int unityFactor(World world, FriendlyUnit[] friendlyUnits) {
        FriendlyUnit[] livingFriends = new FriendlyUnit[4];
        int i = 0;
        for (FriendlyUnit f : friendlyUnits) {
            if (f.getHealth() != 0) {
                livingFriends[i] = f;
                i++;
            }
        }
        switch(i) {
            case 0:
            case 1: return 1;
            case 2: return this.totalDistance(
                        world,
                        livingFriends[0].getPosition(),
                        livingFriends[1].getPosition());
            case 3: return this.totalDistance(
                        world,
                        livingFriends[0].getPosition(),
                        livingFriends[1].getPosition(),
                        livingFriends[2].getPosition());
            case 4: return this.totalDistance(
                        world,
                        livingFriends[0].getPosition(),
                        livingFriends[1].getPosition(),
                        livingFriends[2].getPosition(),
                        livingFriends[3].getPosition());
            default: return -1;
        }
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
        System.out.println(this.unityFactor(world, friendlyUnits));
        if (this.numberOfControlPoints > -1) {
            // Determine if I should shield.
            for (FriendlyUnit f: friendlyUnits) {
                int totalDamage = 0;
                for (EnemyUnit e: enemyUnits) {
                    if (world.canShooterShootTarget(
                        e.getPosition(),
                        f.getPosition(),
                        e.getCurrentWeapon().getRange())) {
                        totalDamage += e.getCurrentWeapon().getDamage();
                    }
                }
                if (totalDamage >= f.getHealth() && f.getNumShields() > 0) {
                    f.activateShields();
                }
            }
            for (FriendlyUnit f: friendlyUnits) {
                Pickup pickupHere = world.getPickupAtPosition(f.getPosition());
                if (pickupHere != null) {
                    if (!this.isGun(pickupHere) || (
                            this.pickupScore(this.gunToPick(f.getCurrentWeapon())) <
                            this.pickupScore(pickupHere.getPickupType()))) {
                        f.pickupItemAtPosition();
                        continue;
                    }
                }
                EnemyUnit myTarget = null;
                int closest = 20;
                for (Direction d: Direction.values()) {
                    if (d == Direction.NOWHERE) {
                        continue;
                    }
                    EnemyUnit e = world.getClosestShootableEnemyInDirection(f, d);
                    if (e == null) {
                        continue;
                    }
                    if (supNormFast(f.getPosition(), e.getPosition()) < closest) {
                        myTarget = e;
                        closest = supNormFast(f.getPosition(), e.getPosition());
                    }
                }
                if (myTarget != null) {
                    f.shootAt(myTarget);
                    continue;
                }
                Point target = null;
                double max = 0;
                for (Pickup p: world.getPickups()) {
                    if (!this.isGun(p)) {
                        double val = 500.0/f.getHealth();
                        if (p.getPickupType() == PickupType.SHIELD) {
                            val *= 3;
                            int playnum = 0;
                            for (FriendlyUnit f : friendlyUnits) {
                                if (f.getHealth() > 0) {
                                    playnum++;
                                }
                            }
                            val /= playnum;
                        }
                        int len = world.getPathLength(f.getPosition(), p.getPosition());
                        if (len != 0) {
                            val /= len;
                            if (val > max) {
                                max = val;
                                target = p.getPosition();
                            }
                        }
                    } else {
                        double val = this.pickupScore(p.getPickupType()) -
                            this.pickupScore(this.gunToPick(f.getCurrentWeapon()));
                        int len = world.getPathLength(f.getPosition(), p.getPosition());
                        if (len != 0) {
                            val /= len;
                            if (val > max) {
                                max = val;
                                target = p.getPosition();
                            }
                        }
                    }
                }
                for (EnemyUnit e: enemyUnits) {
                    if (e.getHealth() == 0) {
                        continue;
                    }
                    double val =
                        this.fudgeFactor*this.pickupScore(this.gunToPick(f.getCurrentWeapon())) -
                        this.pickupScore(this.gunToPick(e.getCurrentWeapon()));
                    int len = world.getPathLength(f.getPosition(), e.getPosition());
                    val /= len;
                    val *= 5;
                    if (val > max) {
                        max = val;
                        target = e.getPosition();
                    }
                }
                if (target != null) {
                    if (f.getLastMoveResult() == MoveResult.BLOCKED_BY_ENEMY) {
                        f.move(directions[(int) (Math.random() *
                            directions.length)]);
                        continue;
                    }
                    f.move(world.getNextDirectionInPath(
                        f.getPosition(),
                        target));
                    continue;
                }
                System.out.println("CAN'T WALK!");
                f.move(Direction.WEST);
            }
        } else {
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
}
