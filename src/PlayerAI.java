import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;


public class PlayerAI {

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

	/**
	 * This method will get called every turn.
	 *
	 * @param world The latest state of the world.
	 * @param enemyUnits An array of all 4 units on the enemy team. Their order won't change.
	 * @param friendlyUnits An array of all 4 units on your team. Their order won't change.
	 */
    public void doMove(World world, EnemyUnit[] enemyUnits, FriendlyUnit[] friendlyUnits) {
        for (FriendlyUnit f: friendlyUnits) {
            f.move(world.getNextDirectionInPath(f.getPosition(),findNearestMainframe(world, f.getPosition()).getPosition()));
        }
    }
}
