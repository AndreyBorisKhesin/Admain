import com.orbischallenge.ctz.Constants;
import com.orbischallenge.game.engine.*;
import com.orbischallenge.ctz.objects.*;
import com.orbischallenge.ctz.objects.enums.*;


public class PlayerAI {

    public PlayerAI() {
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
