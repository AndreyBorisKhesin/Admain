import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;
import com.orbischallenge.game.engine.drawing2.interpolators.InterpolatedObject;

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

    private int totalDistance(World world, Point... ps) {
        int total = 0;
        for (Point p1 : ps) {
            for (Point p2 : ps) {
                total += world.getPathLength(p1, p2);
            }
        }
        return total / 2;
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
	    if (w == null) {
		    return 0;
	    }
        return Math.min(w.getRange(), this.maximumEffectiveRange)
		        * w.getDamage();
    }

    private int modifiedWeaponCoefficient (WeaponType w, World world,
                                           Point point) {
	    return Math.max(weaponCoefficient(w),
			    weaponCoefficient(pickToGun(world.getPickupAtPosition(point))));
    }

    private int unityFactor(World world, FriendlyUnit[] friendlyUnits) {
        Direction[] directions = new Direction[4];
        for (int i = 0; i < directions.length; i++) {
            directions[i] = Direction.NOWHERE;
        }
        return this.unityFactor(world, friendlyUnits, directions);
    }

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
        if (!this.statsSet) {
            this.setStats(world, enemyUnits, friendlyUnits);
        }
        for (FriendlyUnit f : friendlyUnits) {
            System.out.println(f.getLastMoveResult());
        }
        boolean[] moved = new boolean[4];
        for (int i = 0; i < 4; i++) {
	        moved[i] = friendlyUnits[i].getHealth() == 0;
        }
        for (int i = 0; i < 4; i++) {
            if (moved[i]) {
	            continue;
            }
            int totalDamage = 0;
            int enemyNum = 0;
            for (EnemyUnit e: enemyUnits) {
                if (e.getHealth() > 0 && world.canShooterShootTarget(
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
                continue;
            }
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
        int maximumDamage = 0;
        // TODO: Add conditions for a Player not firing at someone due to already having activated a shield.
        // TODO: Add shielding conditions.
        // TODO: Consider the 1 guy keeping two enemies alive situation.
        for (int t0 = 0; t0 < 4; t0++) {
            if (enemyUnits[t0].getHealth() == 0) {
                continue;
            }
            for (int t1 = 0; t1 < 4; t1++) {
                if (enemyUnits[t1].getHealth() == 0) {
                    continue;
                }
                for (int t2 = 0; t2 < 4; t2++) {
                    if (enemyUnits[t2].getHealth() == 0) {
                        continue;
                    }
                    for (int t3 = 0; t3 < 4; t3++) {
                        if (enemyUnits[t3].getHealth() == 0) {
                            continue;
                        }
                        int[] damages = new int[4];
                        byte[] shooters = new byte[4];
                        if (friendlyUnits[0].checkShotAgainstEnemy(enemyUnits[t0]) == ShotResult.CAN_HIT_ENEMY) {
                            damages[t0] += friendlyUnits[0].getCurrentWeapon().getDamage();
                            shooters[t0]++;
                        }
                        if (friendlyUnits[1].checkShotAgainstEnemy(enemyUnits[t1]) == ShotResult.CAN_HIT_ENEMY) {
                            damages[t1] += friendlyUnits[1].getCurrentWeapon().getDamage();
                            shooters[t1]++;
                        }
                        if (friendlyUnits[2].checkShotAgainstEnemy(enemyUnits[t2]) == ShotResult.CAN_HIT_ENEMY) {
                            damages[t2] += friendlyUnits[2].getCurrentWeapon().getDamage();
                            shooters[t2]++;
                        }
                        if (friendlyUnits[3].checkShotAgainstEnemy(enemyUnits[t3]) == ShotResult.CAN_HIT_ENEMY) {
                            damages[t3] += friendlyUnits[3].getCurrentWeapon().getDamage();
                            shooters[t3]++;
                        }
                        int totalDamage = (
                            Math.min(damages[0]*shooters[0], enemyUnits[0].getHealth()) +
                            Math.min(damages[1]*shooters[1], enemyUnits[1].getHealth()) +
                            Math.min(damages[2]*shooters[2], enemyUnits[2].getHealth()) +
                            Math.min(damages[3]*shooters[3], enemyUnits[3].getHealth()));
                        if (totalDamage > maximumDamage) {
                            targets[0] = enemyUnits[t0];
                            targets[1] = enemyUnits[t1];
                            targets[2] = enemyUnits[t2];
                            targets[3] = enemyUnits[t3];
                            maximumDamage = totalDamage;
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
        for (int i = 0; i < 4; i++) {
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
//		            if (supNormFast(friendlyUnits[i].getPosition(),
//				            e.getPosition())
//				            < closest) {
	            if (damage > maxDamage) {
		            maxDamage = damage;
		            myTarget = e;
//			            closest = supNormFast(friendlyUnits[i].getPosition(),
//					            e.getPosition());
	            }
            }
            if (myTarget != null
		            && friendlyUnits[i].getShieldedTurnsRemaining() == 0) {
	            friendlyUnits[i].shootAt(myTarget);
	            moved[i] = true;
                continue;
            }
        }
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
                for (EnemyUnit e : enemyUnits) {
                    if (e.getHealth() == 0) {
                        continue;
                    }
                    double val =
                        this.fudgeFactor * this.weaponCoefficient(
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
				                1000 / world.getPathLength(
				                		Direction.values()[j].movePoint(
				                				friendlyUnits[i].getPosition()),
						                enemyUnit.getPosition()));
	                }
                }
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
            for (int d1 = 0; d1 < Direction.values().length; d1++) {
                Point new1 = Direction.values()[d1].movePoint(
                        friendlyUnits[1].getPosition());
                if (world.getTile(new1) == TileType.WALL
	                    || new1.equals(new0)) {
                    continue;
                }
                for (int d2 = 0; d2 < Direction.values().length; d2++) {
                    Point new2 = Direction.values()[d2].movePoint(
                            friendlyUnits[2].getPosition());
                    if (world.getTile(new2) == TileType.WALL
	                        || new2.equals(new0) || new2.equals(new1)) {
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
                        Direction[] directions = new Direction[4];
                        directions[0] = Direction.values()[d0];
                        directions[1] = Direction.values()[d1];
                        directions[2] = Direction.values()[d2];
                        directions[3] = Direction.values()[d3];
                        int resultingUnity = this.unityFactor(world,
	                            friendlyUnits, directions);
                        double curGoodness = 0;
                        curGoodness += goodness[0][d0];
                        curGoodness += goodness[1][d1];
                        curGoodness += goodness[2][d2];
                        curGoodness += goodness[3][d3];
                        curGoodness *= Math.pow((1d*currentUnity)
	                            / resultingUnity, 0.1);
                        //TODO fiddle with this exponent more
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
            }
        }
    }
}
