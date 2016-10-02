import com.orbischallenge.ctz.objects.enums.Direction;

import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;

import java.util.ArrayList;


public class TriggerHappy {

	static final Direction[] directions = {Direction.EAST, Direction.NORTH,
			Direction.NORTH_EAST, Direction.NORTH_WEST, Direction.SOUTH,
			Direction.SOUTH_EAST, Direction.SOUTH_WEST, Direction.WEST};

    public TriggerHappy() {
    }

//    private ArrayList<Point> path(World world, EnemyUnit[] enemyUnits, Point start,
//                                   Point end) {
//		double[][] distances = new double[worldWidth][worldHeight];
//		boolean[][] visited = new boolean[worldWidth][worldHeight];
//		ArrayList<Point>[][] paths =
//				new ArrayList<Point>[worldWidth][worldHeight];
//		for (int i = 0; i < distances.length; i++) {
//			for (int j = 0; j < distances[i].length; j++) {
//				paths[i][j] = new ArrayList<>();
//				distances[i][j] = i == start.getX() && j == start.getY() ? 0 :
//						Double.MAX_VALUE;
//				if (i == start.getX() && j == start.getY()) {
//					paths[i][j].add(start);
//				}
//			}
//		}
//		Point current = start;
//		while (!visited[end.getX()][end.getY()]) {
//			visited[current.getX()][current.getY()] = true;
//			for (Direction direction : directions) {
//				Point considered = direction.movePoint(current);
//				if (!visited[considered.getX()][considered.getY()]) {
//					double newDistance =
//							distances[current.getX()][current.getY()] + 1;
//					//node weight of considered goes instead of the 1 above
//					if (distances[considered.getX()][considered.getY()] >
//							newDistance) {
//						distances[considered.getX()][considered.getY()] =
//								newDistance;
//						paths[considered.getX()][considered.getY()] =
//								(ArrayList<Point>)
//										paths[current.getX()][current.getY()]
//												.clone();
//						paths[considered.getX()][considered.getY()]
//								.add(considered);
//					}
//				}
//			}
//			double minDistance = Double.MAX_VALUE;
//			Point next = null;
//			for (int i = 0; i < distances.length; i++) {
//				for (int j = 0; j < distances[i].length; j++) {
//					if (!visited[i][j]) {
//						if (distances[i][j] < minDistance) {
//							minDistance = distances[i][j];
//							next = new Point(i, j);
//						}
//					}
//				}
//			}
//			current = next;
//		}
//		return paths[end.getX()][end.getY()];
//	}

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
	    int[][][][] score = new int[directions.length][directions.length]
			    [directions.length][directions.length];
	    for (int i1 = 0; i1 < directions.length; i1++) {
		    for (int i2 = 0; i2 < directions.length; i2++) {
			    for (int i3 = 0; i3 < directions.length; i3++) {
				    for (int i4 = 0; i4 < directions.length; i4++) {
					    score[i1][i2][i3][i4] = -1;
				    }
			    }
		    }
	    }
	    for (int i1 = 0; i1 < directions.length; i1++) {
		    if (world.canMoveFromPointInDirection(
		    		friendlyUnits[1].getPosition(), directions[i1])) {
			    for (int i2 = 0; i2 < directions.length; i2++) {
				    if (world.canMoveFromPointInDirection(
						    friendlyUnits[1].getPosition(), directions[i2])) {
				        for (int i3 = 0; i3 < directions.length; i3++) {
					        if (world.canMoveFromPointInDirection(
							        friendlyUnits[1].getPosition(),
							        directions[i3])) {
					            for (int i4 = 0; i4 < directions.length; i4++) {
						            for (int j = 0; j < enemyUnits.length;
						                 j++) {

						            }
					            }
					        }
					    }
				    }
			    }
		    }
	    }
	    /*
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
	    */
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
