import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;

import java.util.ArrayList;
import java.util.List;

public class PlayerAI {
    /**
     * Whether or not the statistics listed below have been set.
     *
     * On the first turn, this AI precomputes a bunch of statistics that remain
     * constant throughtout the game, to not have to compute them later. This
     * variable is used to indicate that this should only be done on the first turn.
     */
    boolean statsSet;

    /**
     * The maximum distance a gun could be used to fire on this map.
     *
     * On certain maps, there are no long stretches of corridor, so guns cannot
     * reach for their entire range. In this case, we wish to not pick up guns
     * whose only advantage is range. Thus, we compute how far we could
     * actually reach with any gun, and use that to determine how much range is
     * really worth.
     */
    int maximumEffectiveRange;

    /**
     * The directions that all friendly units moved in last turn.
     *
     * This is used to realized situations in which moving in one direction is
     * futile. Should the AI attempt to move a unit in the same direction, but
     * it failed before, we want to try something different. Thus, we store the
     * direction we walked the previous turn, to prevent that.
     */
    Direction[] lastMoves;

    /**
     * Indicates the desire by a given unit to fight.
     *
     * If we have a better weapon than the enemy, we wish to fight them, but if
     * they have a better weapon, then we don't. However, if we have balanced
     * weapons, our behaviour is not so straight forward. This constant is used
     * to indicate our willingness to fight. The greater this constant, the
     * more a unit with equal weapons will wish to fight another.
     */
    final double courageCoefficient = 1.5;

    /**
     * The totalDistance method evaluates how separated the friendly units are
     * by calculating the sum of their pairwise separations.
     * @param world The state of the world, containing a pathfinding method.
     * @param ps The set of points representing unit locations.
     * @return The total pairwise distance between points by shortest paths.
     */
    private int totalDistance(World world, Point... ps) {
        int total = 0;
        for (Point p1 : ps) {
            for (Point p2 : ps) {
                total += world.getPathLength(p1, p2);
            }
        }
        return total / 2;
    }

    /**
     * The enemyNumber method simply compares two teams to determine if they
     * are equal.
     * @param ours The first team.
     * @param other The second team.
     * @return Returns 1 if the teams are the same, -1 if they are different,
     * and 0 if one of the teams is unaffiliated.
     */
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

    /**
     * Initializes the class variables with helpful constants.
     * @param world The world in which the game is taking place.
     * @param enemyUnits The array of enemy units.
     * @param friendlyUnits The array of friendly units.
     */
    private void setStats(
            World world,
            EnemyUnit[] enemyUnits,
            FriendlyUnit[] friendlyUnits) {
        // Iterate along the top row and the left column to determine the size
        // of the world.
        Point p = Point.origin();
        int worldHeight = 0;
        while (world.isWithinBounds(p)) {
            p = p.add(new Point(0,1));
            worldWidth++;
        }
        p = Point.origin();
        int worldHeight = 0;
        while (world.isWithinBounds(p)) {
            p = p.add(new Point(1,0));
            worldHeight++;
        }

        // Iterate over the entire board, and compute the maximum shootable
        // distance for each direction. This is used to compute the maximum
        // effective range, as discussed above. It computes the maximum
        // distance by iteraing down the line of sight, checking for walls.
        this.maximumEffectiveRange = 0;
        for (int x = 0; x < worldWidth; x++) {
            for (int y = 0; y < worldHeight; y++) {
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
        // Indicate that the stats are set, and that we shouldn't run this
        // method anymore.
        this.statsSet = true;
    }

    /**
     * The isGun method determines whether a given item is one of the four guns
     * out of the six items that can be picked up.
     * @param p The item to be picked up.
     * @return Whether or not the item to be picked up is a gun.
     */
    private boolean isGun(Pickup p) {
        if (p == null) {
            return false;
        }
        PickupType pt = p.getPickupType();
        switch(pt) {
            case WEAPON_LASER_RIFLE: return true;
            case WEAPON_MINI_BLASTER: return true;
            case WEAPON_SCATTER_GUN: return true;
            case WEAPON_RAIL_GUN: return true;
            default: return false;
        }
    }

    /**
     * Returns the weapon type of an item that can be picked up if it is a gun.
     * @param p The item to be picked up.
     * @return The weapon type of the gun to be picked up and null otherwise.
     */
    private WeaponType pickToGun (Pickup p) {
        if (p == null) {
            return null;
        }
        PickupType pt = p.getPickupType();
        switch(pt) {
            case WEAPON_LASER_RIFLE: return WeaponType.LASER_RIFLE;
            case WEAPON_MINI_BLASTER: return WeaponType.MINI_BLASTER;
            case WEAPON_SCATTER_GUN: return WeaponType.SCATTER_GUN;
            case WEAPON_RAIL_GUN: return WeaponType.RAIL_GUN;
            default: return null;
        }
    }

    /**
     * The weaponCoefficient method calculates the value of a given gun.
     * @param w The type of gun in question.
     * @return The product of the gun's damage and effective range on the map.
     */
    private int weaponCoefficient (WeaponType w) {
        if (w == null) {
            return 0;
        }
        return Math.min(w.getRange(), this.maximumEffectiveRange)
                * w.getDamage();
    }

    /**
     * The weaponCoefficient method calculates the larger weapon coefficient of
     * a gun and any gun that is located at a given point on the map.
     * @param w The type of weapon that is being considered.
     * @param world The world that the game is taking place in.
     * @param point The point where a second potential weapon could lie.
     * @return The larger weapon coefficient of the two weapons.
     */
    private int modifiedWeaponCoefficient (WeaponType w, World world,
                                           Point point) {
        return Math.max(weaponCoefficient(w),
                weaponCoefficient(pickToGun(world.getPickupAtPosition(point))));
    }

    /**
     * The unityFactor method computes the total distance between the living
     * units out of the friendly units.
     * @param world The world that the game is taking place in.
     * @param friendlyUnits The friendly units whose separation is calculated.
     * @return The total separation between the friendly units.
     */
    private int unityFactor(World world, FriendlyUnit[] friendlyUnits) {
        Direction[] directions = new Direction[4];
        for (int i = 0; i < directions.length; i++) {
            directions[i] = Direction.NOWHERE;
        }
        return this.unityFactor(world, friendlyUnits, directions);
    }

    /**
     * The unityFactor of the friendly units after they have been moved by one
     * tile in a given direction.
     * @param world The world that the game is taking place in.
     * @param friendlyUnits The friendly units whose separation is calculated.
     * @param directions The directions in which all the units move.
     * @return The total separation between the friendly units after a move.
     */
    private int unityFactor(World world, FriendlyUnit[] friendlyUnits,
                            Direction[] directions) {
        Point[] points = new Point[4];
        int alive = 0;
        for (int i = 0; i < 4; i++) {
            if (friendlyUnits[i].getHealth() != 0) {
                points[alive] = directions[i].movePoint(
                        friendlyUnits[i].getPosition());
                alive++;
            }
        }
        switch(alive) {
            case 0:
            case 1: return 1;
            case 2: return this.totalDistance(
                        world,
                        points[0],
                        points[1]);
            case 3: return this.totalDistance(
                        world,
                        points[0],
                        points[1],
                        points[2]);
            case 4: return this.totalDistance(
                        world,
                        points[0],
                        points[1],
                        points[2],
                        points[3]);
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
        // If we haven't computed the statistics yet, remove it.
        if (!this.statsSet) {
            this.setStats(world, enemyUnits, friendlyUnits);
        }
        // Indicates whether or not a given unit has already moved. We use this
        // to avoid overriding instructions we passed already.
        boolean[] moved = new boolean[4];
        // Dead units should not get instructions.
        for (int i = 0; i < 4; i++) {
            moved[i] = friendlyUnits[i].getHealth() == 0;
        }
        // For each unit, do the following...
        for (int i = 0; i < 4; i++) {
            if (moved[i]) {
                continue;
            }
            int totalDamage = 0;
            int enemyNum = 0;
            // Compute the total amount of damage the given unit might receive
            // this turn.
            for (EnemyUnit e: enemyUnits) {
                if (e.getHealth() > 0 && world.canShooterShootTarget(
                    e.getPosition(),
                    friendlyUnits[i].getPosition(),
                    e.getCurrentWeapon().getRange())) {
                    totalDamage += e.getCurrentWeapon().getDamage();
                    enemyNum++;
                }
            }
            // If this unit might die this turn, and it has a shield, activate
            // it.
            if (totalDamage * enemyNum >= friendlyUnits[i].getHealth()
                    && friendlyUnits[i].getNumShields() > 0
                    && friendlyUnits[i].getShieldedTurnsRemaining() == 0) {
                friendlyUnits[i].activateShield();
                moved[i] = true;
                continue;
            }
            // Otherwise, determine whether the unit should pick up the pickup
            // it's standing on. We always want shields and repair kits, but a
            // gun should only be picked up if it's better than our current.
            Pickup pickupHere = world.getPickupAtPosition(
                    friendlyUnits[i].getPosition());
            if (pickupHere != null) {
                if (!this.isGun(pickupHere) || (
                        this.weaponCoefficient(
                                friendlyUnits[i].getCurrentWeapon()) <
                        this.weaponCoefficient(
                                this.pickToGun(pickupHere)))) {
                    friendlyUnits[i].pickupItemAtPosition();
                    moved[i] = true;
                    continue;
                }
            }
        }
        EnemyUnit[] targets = new EnemyUnit[4];
        double maximumScore = 0;
        for (int t0 = 0; t0 < 4; t0++) {
            if (enemyUnits[t0].getHealth() == 0
                    || enemyUnits[t0].getShieldedTurnsRemaining() > 0) {
                continue;
            }
            for (int t1 = 0; t1 < 4; t1++) {
                if (enemyUnits[t1].getHealth() == 0
                        || enemyUnits[t1].getShieldedTurnsRemaining() > 0) {
                    continue;
                }
                for (int t2 = 0; t2 < 4; t2++) {
                    if (enemyUnits[t2].getHealth() == 0
                            || enemyUnits[t2].getShieldedTurnsRemaining() > 0) {
                        continue;
                    }
                    for (int t3 = 0; t3 < 4; t3++) {
                        if (enemyUnits[t3].getHealth() == 0
                                || enemyUnits[t3].getShieldedTurnsRemaining()
                                > 0) {
                            continue;
                        }
                        int[] damages = new int[4];
                        int[] shooters = new int[4];
                        for (int i = 0; i < 4; i++) {
                            damages[i] = 0;
                            shooters[i] = 0;
                        }
                        if (friendlyUnits[0].checkShotAgainstEnemy(
                                enemyUnits[t0]) == ShotResult.CAN_HIT_ENEMY
                                && friendlyUnits[0].getShieldedTurnsRemaining()
                                == 0) {
                            damages[t0] += friendlyUnits[0].getCurrentWeapon()
                                    .getDamage();
                            shooters[t0]++;
                        }
                        if (friendlyUnits[1].checkShotAgainstEnemy(
                                enemyUnits[t1]) == ShotResult.CAN_HIT_ENEMY
                                && friendlyUnits[1].getShieldedTurnsRemaining()
                                == 0) {
                            damages[t1] += friendlyUnits[1].getCurrentWeapon()
                                    .getDamage();
                            shooters[t1]++;
                        }
                        if (friendlyUnits[2].checkShotAgainstEnemy(
                                enemyUnits[t2]) == ShotResult.CAN_HIT_ENEMY
                                && friendlyUnits[2].getShieldedTurnsRemaining()
                                == 0) {
                            damages[t2] += friendlyUnits[2].getCurrentWeapon()
                                    .getDamage();
                            shooters[t2]++;
                        }
                        if (friendlyUnits[3].checkShotAgainstEnemy(
                                enemyUnits[t3]) == ShotResult.CAN_HIT_ENEMY
                                && friendlyUnits[3].getShieldedTurnsRemaining()
                                == 0) {
                            damages[t3] += friendlyUnits[3].getCurrentWeapon()
                                    .getDamage();
                            shooters[t3]++;
                        }
                        double score = 0.0;
                        if (enemyUnits[0].getHealth() > 0) {
                            score += (1d * Math.min(damages[0] * shooters[0],
                                enemyUnits[0].getHealth()))
                                / enemyUnits[0].getHealth();
                        }
                        if (enemyUnits[1].getHealth() > 0) {
                        score += (1d * Math.min(damages[1] * shooters[1],
                                enemyUnits[1].getHealth()))
                                / enemyUnits[1].getHealth();
                        }
                        if (enemyUnits[2].getHealth() > 0) {
                        score += (1d * Math.min(damages[2] * shooters[2],
                                enemyUnits[2].getHealth()))
                                / enemyUnits[2].getHealth();
                        }
                        if (enemyUnits[3].getHealth() > 0) {
                        score += (1d * Math.min(damages[3] * shooters[3],
                                enemyUnits[3].getHealth()))
                                / enemyUnits[3].getHealth();
                        }
                        if (score > maximumScore) {
                            targets[0] = enemyUnits[t0];
                            targets[1] = enemyUnits[t1];
                            targets[2] = enemyUnits[t2];
                            targets[3] = enemyUnits[t3];
                            maximumScore = score;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            if (moved[i]) {
                continue;
            }
            if (targets[i] != null) {
                if (friendlyUnits[i].checkShotAgainstEnemy(targets[i]) ==
                        ShotResult.CAN_HIT_ENEMY) {
                    friendlyUnits[i].shootAt(targets[i]);
                    moved[i] = true;
                }
            }
        }
        /*for (int i = 0; i < 4; i++) {
            if (moved[i]) {
                continue;
            }
            EnemyUnit myTarget = null;
            //int closest = 20;
            int maxDamage = Integer.MIN_VALUE;
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
                int damage = 0;
                int shooters = 0;
                for (int j = 0; j < 4; j++) {
                    if (world.canShooterShootTarget(
                            friendlyUnits[j].getPosition(), e.getPosition(),
                            friendlyUnits[j].getCurrentWeapon().getRange())
                            && friendlyUnits[j].getShieldedTurnsRemaining()
                            == 0 && !moved[j]){
                        shooters++;
                        damage += friendlyUnits[j].getCurrentWeapon()
                                .getDamage();
                    }
                }
                damage = Math.min(damage * shooters, e.getHealth());
//                    if (supNormFast(friendlyUnits[i].getPosition(),
//                            e.getPosition())
//                            < closest) {
                if (damage > maxDamage) {
                    maxDamage = damage;
                    myTarget = e;
//                        closest = supNormFast(friendlyUnits[i].getPosition(),
//                                e.getPosition());
                }
            }
            if (myTarget != null
                    && friendlyUnits[i].getShieldedTurnsRemaining() == 0) {
                friendlyUnits[i].shootAt(myTarget);
                moved[i] = true;
                continue;
            }
        }*/
        // for each person, for each direction,
        // take maximum of each possible target value.
        // Memoize the goodness for this person direction thing.
        double[][] goodness =
            new double[friendlyUnits.length][Direction.values().length];
        for (int i = 0; i < friendlyUnits.length; i++) {
            for (int j = 0; j < Direction.values().length; j++) {
                goodness[i][j] = -15000000;
                if (moved[i]) {
                    if (Direction.values()[j] == Direction.NOWHERE) {
                        goodness[i][j] = 0;
                    }
                    continue;
                }
                Point newStart = Direction.values()[j].movePoint(
                        friendlyUnits[i].getPosition());
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
                                val = -15000000;
                            } else {
                                val *= 1000.0
                                        / friendlyUnits[i].getHealth();
                            }
                            break;
                        case WEAPON_LASER_RIFLE:
                        case WEAPON_MINI_BLASTER:
                        case WEAPON_SCATTER_GUN:
                        case WEAPON_RAIL_GUN:
                            WeaponType otherGun = this.pickToGun(p);
                            WeaponType myGun =
                                    friendlyUnits[i].getCurrentWeapon();
                            val *= this.weaponCoefficient(otherGun)
                                    - this.weaponCoefficient(myGun);
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

                if (!(friendlyUnits[i].getShieldedTurnsRemaining() > 0)) {
                    for (EnemyUnit e : enemyUnits) {
                        if (e.getHealth() == 0) {
                            continue;
                        }
                        double val =
                            this.courageCoefficient * this.weaponCoefficient(
                                    friendlyUnits[i].getCurrentWeapon()) -
                            this.modifiedWeaponCoefficient(e.getCurrentWeapon(),
                                    world, e.getPosition());
                        int len = world.getPathLength(newStart,
                                e.getPosition());
                        val /= len + 1;
                        val *= 5;
                        if (val >= goodness[i][j]) {
                            goodness[i][j] = val;
                        }
                    }
                }
                int ourMainframes = 0;
                int theirMainframes = 0;
                for (ControlPoint cp : world.getControlPoints()) {
                    if (cp.isMainframe()
                            && enemyNumber(friendlyUnits[i].getTeam(),
                            cp.getControllingTeam()) == 1) {
                        ourMainframes++;
                    }
                    if (cp.isMainframe()
                            && enemyNumber(friendlyUnits[i].getTeam(),
                            cp.getControllingTeam()) == -1) {
                        theirMainframes++;
                    }
                }
                for (ControlPoint cp : world.getControlPoints()) {
                    double val = 25;
                    int len = world.getPathLength(newStart,
                            cp.getPosition());
                    if (len != 0) {
                        val /= len;
                    }
                    if (enemyNumber(friendlyUnits[i].getTeam(),
                            cp.getControllingTeam()) == 1) {
                        val = -15000000;
                    }
                    if (cp.isMainframe()) {
                        val *= 3;
                        val /= (ourMainframes + 0.5);
                    }
                    if (theirMainframes == 1) {
                        val *= 2;
                    }
                    if (val >= goodness[i][j]) {
                        goodness[i][j] = val;
                    }
                }
                for (EnemyUnit enemyUnit : enemyUnits) {
                    if (enemyUnit.getHealth() > 0
                            && world.canShooterShootTarget(
                            Direction.values()[j].movePoint(
                                    friendlyUnits[i].getPosition()),
                            enemyUnit.getPosition(),
                            friendlyUnits[i].getCurrentWeapon()
                                    .getRange())) {
                        goodness[i][j] = Math.max(goodness[i][j],
                                100d / world.getPathLength(
                                        Direction.values()[j].movePoint(
                                                friendlyUnits[i].getPosition()),
                                        enemyUnit.getPosition()));
                    }
                }
            }
        }
        int minDistance = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (friendlyUnits[i].getHealth() == 0) {
                continue;
            }
            for (int j = 0; j < 4; j++) {
                if (enemyUnits[j].getHealth() == 0) {
                    continue;
                }
                minDistance = Math.min(minDistance, world.getPathLength(
                        friendlyUnits[i].getPosition(),
                        enemyUnits[j].getPosition()));
            }
        }
        int[] optimalDirections = new int[4];
        double maximumGoodness = Double.MIN_VALUE;
        int currentUnity = this.unityFactor(world, friendlyUnits);
        for (int d0 = 0; d0 < Direction.values().length; d0++) {
            Point new0 = Direction.values()[d0].movePoint(
                    friendlyUnits[0].getPosition());
            if (world.getTile(new0) == TileType.WALL) {
                continue;
            }
            boolean lastMoveFailed0 = (
                    !friendlyUnits[0].didLastActionSucceed() &&
                    friendlyUnits[0].getLastMoveResult() == MoveResult.BLOCKED_BY_ENEMY);
            if (this.lastMoves[0] == Direction.values()[d0] && lastMoveFailed0) {
                continue;
            }
            for (int d1 = 0; d1 < Direction.values().length; d1++) {
                Point new1 = Direction.values()[d1].movePoint(
                        friendlyUnits[1].getPosition());
                if (world.getTile(new1) == TileType.WALL
                        || new1.equals(new0)) {
                    continue;
                }
                boolean lastMoveFailed1 = (
                        !friendlyUnits[1].didLastActionSucceed() &&
                        friendlyUnits[1].getLastMoveResult() == MoveResult.BLOCKED_BY_ENEMY);
                if (this.lastMoves[1] == Direction.values()[d1] && lastMoveFailed1) {
                    continue;
                }
                for (int d2 = 0; d2 < Direction.values().length; d2++) {
                    Point new2 = Direction.values()[d2].movePoint(
                            friendlyUnits[2].getPosition());
                    if (world.getTile(new2) == TileType.WALL
                            || new2.equals(new0) || new2.equals(new1)) {
                        continue;
                    }
                    boolean lastMoveFailed2 = (
                            !friendlyUnits[2].didLastActionSucceed() &&
                            friendlyUnits[2].getLastMoveResult() == MoveResult.BLOCKED_BY_ENEMY);
                    if (this.lastMoves[2] == Direction.values()[d2] && lastMoveFailed2) {
                        continue;
                    }
                    for (int d3 = 0; d3 < Direction.values().length; d3++) {
                        Point new3 = Direction.values()[d3].movePoint(
                                friendlyUnits[3].getPosition());
                        if (world.getTile(new3) == TileType.WALL
                                || new3.equals(new0) || new3.equals(new1)
                                || new3.equals(new2)) {
                            continue;
                        }
                        boolean lastMoveFailed3 = (
                                !friendlyUnits[3].didLastActionSucceed() &&
                                friendlyUnits[3].getLastMoveResult() == MoveResult.BLOCKED_BY_ENEMY);
                        if (this.lastMoves[3] == Direction.values()[d3] && lastMoveFailed3) {
                            continue;
                        }
                        Direction[] directions = new Direction[4];
                        directions[0] = Direction.values()[d0];
                        directions[1] = Direction.values()[d1];
                        directions[2] = Direction.values()[d2];
                        directions[3] = Direction.values()[d3];
                        int resultingUnity = this.unityFactor(world,
                                friendlyUnits, directions);
                        int[] resultingHealth = {1, 1, 1, 1};
                        for (int i = 0; i < friendlyUnits.length; i++) {
                            if (friendlyUnits[i].getHealth() == 0) {
                                continue;
                            }
                            int enemyNum = 0;
                            int damage = 0;
                            for (EnemyUnit e : enemyUnits) {
                                if (e.getHealth() > 0
                                        && world.canShooterShootTarget(
                                        e.getPosition(),
                                        directions[i].movePoint(
                                                friendlyUnits[i].getPosition()),
                                        e.getCurrentWeapon().getRange())) {
                                    damage += e.getCurrentWeapon().getDamage();
                                    enemyNum++;
                                }
                            }
                            resultingHealth[i] *= Math.max(0,
                                    friendlyUnits[i].getHealth()
                                            - enemyNum * damage) + 1;
                        }
                        double curGoodness = 0;
                        curGoodness += goodness[0][d0]
                                * (Direction.values()[d0] == Direction.NOWHERE
                                && lastMoveFailed0 ? 0.5 : 1)
                                * resultingHealth[0];
                        curGoodness += goodness[1][d1]
                                * (Direction.values()[d0] == Direction.NOWHERE
                                && lastMoveFailed1 ? 0.5 : 1)
                                * resultingHealth[1];
                        curGoodness += goodness[2][d2]
                                * (Direction.values()[d0] == Direction.NOWHERE
                                && lastMoveFailed2 ? 0.5 : 1)
                                * resultingHealth[2];
                        curGoodness += goodness[3][d3]
                                * (Direction.values()[d0] == Direction.NOWHERE
                                && lastMoveFailed3 ? 0.5 : 1)
                                * resultingHealth[3];
                        curGoodness *= Math.pow(1d * currentUnity
                                / resultingUnity, 1d / minDistance);
                        if (curGoodness > maximumGoodness) {
                            maximumGoodness = curGoodness;
                            optimalDirections[0] = d0;
                            optimalDirections[1] = d1;
                            optimalDirections[2] = d2;
                            optimalDirections[3] = d3;
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            if (!moved[i]) {
                friendlyUnits[i].move(
                        Direction.values()[optimalDirections[i]]);
                lastMoves[i] = Direction.values()[optimalDirections[i]];
            } else {
                lastMoves[i] = null;
            }
        }
    }
}
