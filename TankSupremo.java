package tank;

import robocode.*;
import java.awt.*;
import java.util.Random;

public class TankSupremo extends AdvancedRobot {
    private double enemyBearing;
    private double enemyDistance;
    private double enemyHeading;
    private double enemyVelocity;
    private double previousEnergy = 100;
    private Random random = new Random();

    public void run() {
        setColors(Color.BLACK, Color.RED, Color.YELLOW); // corpo, arma, radar
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            turnRadarRight(360); // radar sempre ativo
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        enemyBearing = e.getBearing();
        enemyDistance = e.getDistance();
        enemyHeading = e.getHeading();
        enemyVelocity = e.getVelocity();

        // Mira preditiva: tenta acertar onde o inimigo vai estar
        double bulletPower = Math.min(3.0, getEnergy() / 20);
        double myX = getX();
        double myY = getY();
        double absoluteBearing = getHeading() + e.getBearing();
        double enemyX = myX + Math.sin(Math.toRadians(absoluteBearing)) * e.getDistance();
        double enemyY = myY + Math.cos(Math.toRadians(absoluteBearing)) * e.getDistance();
        double deltaTime = 0;
        double predictedX = enemyX, predictedY = enemyY;

        while ((++deltaTime) * (20.0 - 3.0 * bulletPower) < Point2D.distance(myX, myY, predictedX, predictedY)) {
            predictedX += Math.sin(Math.toRadians(enemyHeading)) * enemyVelocity;
            predictedY += Math.cos(Math.toRadians(enemyHeading)) * enemyVelocity;
            if (predictedX < 18.0 || predictedY < 18.0 || predictedX > getBattleFieldWidth() - 18.0 || predictedY > getBattleFieldHeight() - 18.0) {
                predictedX = Math.min(Math.max(18.0, predictedX), getBattleFieldWidth() - 18.0);
                predictedY = Math.min(Math.max(18.0, predictedY), getBattleFieldHeight() - 18.0);
                break;
            }
        }

        double theta = Math.toDegrees(Math.atan2(predictedX - myX, predictedY - myY));
        setTurnGunRight(normalizeBearing(theta - getGunHeading()));
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            fire(bulletPower);
        }

        // Movimento evasivo
        if (enemyDistance < 200) {
            setBack(100 + random.nextInt(50));
        } else {
            setAhead(150 + random.nextInt(100));
        }

        // Movimento circular aleatório
        setTurnRight(e.getBearing() + 90 - 30 * (random.nextDouble() - 0.5));

        // Radar travado no inimigo
        double radarTurn = getHeading() - getRadarHeading() + e.getBearing();
        setTurnRadarRight(2 * normalizeBearing(radarTurn));

        // Recuperação estratégica (mantém energia alta)
        if (getEnergy() < 30 && enemyDistance > 150) {
            // recua e economiza tiros até recuperar
            setBack(100);
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Movimento aleatório ao ser atingido
        setTurnRight(90 - e.getBearing() + (random.nextDouble() * 90 - 45));
        setAhead(100 + random.nextInt(100));
    }

    public void onHitWall(HitWallEvent e) {
        setBack(50);
        setTurnRight(90);
    }

    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
