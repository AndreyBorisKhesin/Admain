import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;

import java.util.ArrayList;
import java.util.List;

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
    Direction[][][] possibleDirections;
    // int averageEffectiveRange;
    //

    double fudgeFactor = 1.01;

    public PlayerAI() {
        statsSet = false;
    }

    /*private ArrayList<Point> path(World world, EnemyUnit[] enemyUnits,
                                  Point start, Point end,
                                  FriendlyUnit friendlyUnit) {
        double[][] distances = new double[worldWidth][worldHeight];
        boolean[][] visited = new boolean[worldWidth][worldHeight];
        List<List<List<Point>>> paths = new ArrayList<>();
        for (int i = 0; i < distances.length; i++) {
            paths.add(new ArrayList<>());
            for (int j = 0; j < distances[i].length; j++) {
                paths.get(i).add(new ArrayList<>());
                distances[i][j] = i == start.getX() && j == start.getY() ? 0 :
                        Double.MAX_VALUE;
                if (i == start.getX() && j == start.getY()) {
                    paths.get(i).get(j).add(start);
                }
            }
        }
        Point current = start;
        while (
                world.isWithinBounds(end) &&
                !visited[end.getX()][end.getY()]) {
            visited[current.getX()][current.getY()] = true;
            for (Direction direction : directions) {
                Point considered = direction.movePoint(current);
                if (
                        world.isWithinBounds(considered) &&
                        world.getTile(considered) != TileType.WALL &&
                        !visited[considered.getX()][considered.getY()]) {
                    double nodeWeight = 1;
                    for (EnemyUnit enemyUnit : enemyUnits) {
                        nodeWeight *= (2 * enemyUnit.getHealth()
                                / friendlyUnit.getCurrentWeapon().getRange()
                                / friendlyUnit.getCurrentWeapon().getDamage()
                                - friendlyUnit.getHealth()
                                / enemyUnit.getCurrentWeapon().getRange()
                                / enemyUnit.getCurrentWeapon().getDamage())
                                / (world.getPathLength(considered,
                                enemyUnit.getPosition()) + 1)
                                / (world.getPathLength(considered,
                                enemyUnit.getPosition()) + 1);
                    }
                    double newDistance =
                            distances[current.getX()][current.getY()] +
                                    Math.max(1, nodeWeight + 1);
                    if (distances[considered.getX()][considered.getY()] >
                            newDistance) {
                        distances[considered.getX()][considered.getY()] =
                                newDistance;
                        paths.get(considered.getX()).set(considered.getY(),
                                paths.get(current.getX())
                                        .get(current.getY()));
                        paths.get(considered.getX()).get(considered.getY())
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
        return (ArrayList<Point>) paths.get(end.getX()).get(end.getY());
    }*/

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
        this.worldHeight = 0;
        while (world.isWithinBounds(p)) {
            p = p.add(new Point(0,1));
            this.worldWidth++;
        }
        p = Point.origin();
        this.worldHeight = 0;
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
        PickupType pt = p.getPickupType();
        switch(pt) {
            case WEAPON_LASER_RIFLE: return WeaponType.LASER_RIFLE;
            case WEAPON_MINI_BLASTER: return WeaponType.MINI_BLASTER;
            case WEAPON_SCATTER_GUN: return WeaponType.SCATTER_GUN;
            case WEAPON_RAIL_GUN: return WeaponType.RAIL_GUN;
            default: return null;
        }
    }

    private PickupType gunToPick (WeaponType w) {
        switch(w) {
            case LASER_RIFLE: return PickupType.WEAPON_LASER_RIFLE;
            case MINI_BLASTER: return PickupType.WEAPON_MINI_BLASTER;
            case SCATTER_GUN: return PickupType.WEAPON_SCATTER_GUN;
            case RAIL_GUN: return PickupType.WEAPON_RAIL_GUN;
            default: return null;
        }
    }

    private int weaponCoefficient (WeaponType w) {
        return Math.min(w.getRange(), this.maximumEffectiveRange) * w.getDamage();
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
            boolean[] moved = new boolean[4];
            // Determine if I should shield.
            for (int i = 0; i < 4; i++) {
                int totalDamage = 0;
                int enemyNum = 0;
                for (EnemyUnit e: enemyUnits) {
                    if (world.canShooterShootTarget(
                        e.getPosition(),
                        friendlyUnits[i].getPosition(),
                        e.getCurrentWeapon().getRange())) {
                        totalDamage += e.getCurrentWeapon().getDamage();
                        enemyNum++;
                    }
                }
                if (totalDamage * enemyNum >= friendlyUnits[i].getHealth()
                        && friendlyUnits[i].getNumShields() > 0
                        && friendlyUnits[i].getShieldedTurnsRemaining() == 0) {
                    friendlyUnits[i].activateShield();
                    moved[i] = true;
                }
            }
            // for each person, for each direction, take maximum of each possible target value.
            // Memoize the goodness for this person direction thing.
            double[][] goodness =
                new double[friendlyUnits.length][Direction.values().length];
            for (int i = 0; i < friendlyUnits.length; i++) {
                for (int j = 0; j < Direction.values().length; j++) {
                    goodness[i][j] = Double.MIN_VALUE;
                    Point newStart = Direction.values()[j].movePoint(friendlyUnits[i].getPosition());
                    if (world.getTile(newStart) == TileType.WALL) {
                        continue;
                    }
                    for (Pickup p: world.getPickups()) {
                        double val = 1.0;
                        switch(p.getPickupType()) {
                            case SHIELD:
                                val *= 3;
                                int playnum = 0;
                                for (FriendlyUnit fu : friendlyUnits) {
                                    if (fu.getHealth() > 0) {
                                        playnum++;
                                    }
                                }
                                if (playnum != 0) {
                                    val /= playnum;
                                }
                            case REPAIR_KIT:
                                if (friendlyUnits[i].getHealth() == 0) {
                                    val = Double.MIN_VALUE;
                                } else {
                                    val *= 500.0 / friendlyUnits[i].getHealth();
                                }
                                break;
                            case WEAPON_LASER_RIFLE:
                            case WEAPON_MINI_BLASTER:
                            case WEAPON_SCATTER_GUN:
                            case WEAPON_RAIL_GUN:
                                WeaponType otherGun = this.pickToGun(p);
                                WeaponType myGun = friendlyUnits[i].getCurrentWeapon();
                                val *= this.weaponCoefficient(otherGun) - this.weaponCoefficient(myGun);
                                break;
                        }
                        int len = world.getPathLength(
                                newStart,
                                p.getPosition());
                        if (len != 0) {
                            val /= len + 1;
                        }
                        if (val >= goodness[i][j]) {
                            goodness[i][j] = val;
                        }
                    }
                    for (EnemyUnit e : enemyUnits) {
                        if (e.getHealth() == 0) {
                            continue;
                        }
                        double val =
                            this.fudgeFactor * this.weaponCoefficient(friendlyUnits[i].getCurrentWeapon()) -
                            this.weaponCoefficient(e.getCurrentWeapon());
                        int len = world.getPathLength(newStart,
                                e.getPosition());
                        val /= len+1;
                        val *= 5;
                        if (val >= goodness[i][j]) {
                            goodness[i][j] = val;
                        }
                    }
                    int ourMainframes = 0;
                    int theirMainframes = 0;
                    for (ControlPoint cp : world.getControlPoints()) {
                        if (cp.isMainframe() && enemyNumber(friendlyUnits[i].getTeam(), cp.getControllingTeam()) == 1) {
                            ourMainframes++;
                        }
                        if (cp.isMainframe() && enemyNumber(friendlyUnits[i].getTeam(), cp.getControllingTeam()) == -1) {
                            theirMainframes++;
                        }
                    }
                    for (ControlPoint cp : world.getControlPoints()) {
                        double val = 25;
                        int len = world.getPathLength(newStart,
                                cp.getPosition());
                        if (len != 0) {
                            val /= len+1;
                        }
                        if (enemyNumber(friendlyUnits[i].getTeam(), cp.getControllingTeam()) == 1) {
                            val = Double.MIN_VALUE;
                        }
                        if (cp.isMainframe()) {
                            val *= 3;
                            val /= (ourMainframes+0.5);
                        }
                        if (theirMainframes == 1) {
                            val *= 2;
                        }
                        if (val >= goodness[i][j]) {
                            goodness[i][j] = val;
                        }
                    }
                }
            }
            int[] optimalDirections = new int[4];
            double maximumGoodness = Double.MIN_VALUE;
            int total = 0;
            for (int d0 = 0; d0 < Direction.values().length; d0++) {
                if (world.getTile(
                        Direction.values()[d0].movePoint(friendlyUnits[0].getPosition())) == TileType.WALL) {
                    continue;
                }
                for (int d1 = 0; d1 < Direction.values().length; d1++) {
                    if (world.getTile(
                            Direction.values()[d1].movePoint(friendlyUnits[1].getPosition())) == TileType.WALL) {
                        continue;
                    }
                    for (int d2 = 0; d2 < Direction.values().length; d2++) {
                        if (world.getTile(
                                Direction.values()[d2].movePoint(friendlyUnits[2].getPosition())) == TileType.WALL) {
                            continue;
                        }
                        for (int d3 = 0; d3 < Direction.values().length; d3++) {
                            if (world.getTile(
                                    Direction.values()[d3].movePoint(friendlyUnits[3].getPosition())) == TileType.WALL) {
                                continue;
                            }
                            // TODO: Add check for collision.
                            // TODO: Add clumping mechanism.
                            // TODO: Choosing to shoot.
                            double curGoodness = 0;
                            curGoodness += goodness[0][d0];
                            curGoodness += goodness[1][d1];
                            curGoodness += goodness[2][d2];
                            curGoodness += goodness[3][d3];
                            if (curGoodness > maximumGoodness) {
                                maximumGoodness = curGoodness;
                                optimalDirections[0] = d0;
                                optimalDirections[1] = d1;
                                optimalDirections[2] = d2;
                                optimalDirections[3] = d3;
                            }
                            total++;
                        }
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                if (moved[i]) {
                    continue;
                }
                Pickup pickupHere = world.getPickupAtPosition(friendlyUnits[i].getPosition());
                if (pickupHere != null) {
                    if (!this.isGun(pickupHere) || (
                                this.weaponCoefficient(friendlyUnits[i].getCurrentWeapon()) <
                                this.weaponCoefficient(this.pickToGun(pickupHere)))) {
                        friendlyUnits[i].pickupItemAtPosition();
                        continue;
                    }
                }
                EnemyUnit myTarget = null;
                int closest = 20;
                for (Direction d: Direction.values()) {
                    if (d == Direction.NOWHERE) {
                        continue;
                    }
                    EnemyUnit e = world.getClosestShootableEnemyInDirection(
                            friendlyUnits[i],
                            d);
                    if (e == null || e.getShieldedTurnsRemaining() > 0) {
                        continue;
                    }
                    if (supNormFast(friendlyUnits[i].getPosition(), e.getPosition())
                            < closest) {
                        myTarget = e;
                        closest = supNormFast(friendlyUnits[i].getPosition(), e.getPosition());
                    }
                }
                if (myTarget != null
                        && friendlyUnits[i].getShieldedTurnsRemaining() == 0) {
                    friendlyUnits[i].shootAt(myTarget);
                    continue;
                }
                friendlyUnits[i].move(Direction.values()[optimalDirections[i]]);
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

