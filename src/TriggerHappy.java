import com.orbischallenge.ctz.objects.enums.Direction;

import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.sun.glass.ui.SystemClipboard;


public class PlayerAI {

	static final Direction[] directions = {Direction.EAST, Direction.NORTH,
			Direction.NORTH_EAST, Direction.NORTH_WEST, Direction.SOUTH,
			Direction.SOUTH_EAST, Direction.SOUTH_WEST, Direction.WEST};

    public PlayerAI() {
    }

    private ControlPoint findNearestMainframe(World world, Point p) {
        ControlPoint[] allPoints = world.getControlPoints();
        ControlPoint closest = null;
        int longest = 1000;
        for (ControlPoint cp: allPoints) {
            if (cp.isMainframe()) {
                int len = world.getPathLength(p, cp.getPosition());
                if (len < longest) {
                    longest = len;
                    closest = cp;
                }
            }
        }
        if (closest == null) {
            closest = world.getNearestControlPoint(p);
        }
        return closest;
    }

    private int supNorm(Point a, Point b) {
	    if (a == null || b == null) {
		    return 1000;
	    }
	    return Math.max(Math.abs(a.getX() - b.getX()),
			    Math.abs(a.getY() - b.getY()));
    }

    private EnemyUnit nearestShootableEnemy(World world,
                                            FriendlyUnit friendlyUnit) {
	    int minDistanceToShootableEnemy = 1000;
	    EnemyUnit target = null;
	    for (Direction direction : directions) {
		    EnemyUnit enemyUnit =
				    world.getClosestShootableEnemyInDirection(friendlyUnit,
						    direction);
		    if (enemyUnit != null) {
			    int newDistance =
					    supNorm(enemyUnit.getPosition(),
							    friendlyUnit.getPosition());
			    if (newDistance < minDistanceToShootableEnemy) {
				    minDistanceToShootableEnemy = newDistance;
				    target = enemyUnit;
			    }
		    }
	    }
	    return target;
    }

	/**
	 * This method will get called every turn.
	 *
	 * @param world The latest state of the world.
	 * @param enemyUnits An array of all 4 units on the enemy team.
	 *                      Their order won't change.
	 * @param friendlyUnits An array of all 4 units on your team.
	 *                         Their order won't change.
	 */
    public void doMove(World world, EnemyUnit[] enemyUnits,
                       FriendlyUnit[] friendlyUnits) {
	    boolean[] moved = new boolean[friendlyUnits.length];
	    for (int i = 0; i < friendlyUnits.length; i++) {
		    FriendlyUnit friendlyUnit = friendlyUnits[i];
		    EnemyUnit target = nearestShootableEnemy(world, friendlyUnit);
		    if (target != null) {
			    friendlyUnit.shootAt(target);
			    moved[i] = true;
		    }
	    }
	    for (int i = 0; i < friendlyUnits.length; i++) {
		    if (!moved[i]) {
			    for (Direction direction : directions) {
				    for (EnemyUnit enemyUnit : enemyUnits) {
				        if (world.canShooterShootTarget(
				        		direction.movePoint(
				        				friendlyUnits[i].getPosition()),
							    enemyUnit.getPosition(),
							    friendlyUnits[i].getCurrentWeapon()
									    .getRange())) {
					        friendlyUnits[i].move(direction);
					        moved[i] = true;
				        }
				    }
			    }
		    }
	    }
        for (int i = 0; i < friendlyUnits.length; i++) {
	        if (!moved[i]) {
		        if (findNearestMainframe(world, friendlyUnits[i].getPosition())
				        == null) {
			        friendlyUnits[i].move(directions[(int) (Math.random() *
					        directions.length)]);
		        } else {
			        friendlyUnits[i].move(world.getNextDirectionInPath(
			        		friendlyUnits[i].getPosition(),
					        findNearestMainframe(world,
							        friendlyUnits[i].getPosition())
							        .getPosition()));
		        }
	        }
        }
    }
}
