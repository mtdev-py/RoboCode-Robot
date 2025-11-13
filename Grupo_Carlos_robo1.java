package Exemplo;
import robocode.*;
import java.awt.*;
import java.awt.geom.Point2D;

public class Grupo_Carlos_robo1 extends AdvancedRobot {

    private double enemyBearing;
    private double enemyDistance;
    private double enemyHeading;
    private double enemyVelocity;
    private int moveDirection = 1; // controla direção do movimento

    public void run() {
        // Cores do robô
        setColors(Color.BLACK, Color.YELLOW, Color.RED, Color.ORANGE, Color.WHITE);

        // Permite que arma e radar se movam independentemente
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        // Loop principal
        while (true) {
            // Mantém o radar girando constantemente (lock constante)
            turnRadarRight(360);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // --- Dados do inimigo ---
        enemyBearing = e.getBearing();
        enemyDistance = e.getDistance();
        enemyHeading = e.getHeading();
        enemyVelocity = e.getVelocity();

        // --- Radar Lock ---
        double radarTurn = getHeading() + e.getBearing() - getRadarHeading();
        setTurnRadarRight(normalizeBearing(radarTurn) * 2); // multiplica por 2 para garantir travamento

        // --- Movimento evasivo circular ---
        setTurnRight(e.getBearing() + 90 - 30 * moveDirection);
        setAhead(150 * moveDirection);

        // --- Mira preditiva simples ---
        double bulletPower = Math.min(3.0, getEnergy() / 10);
        double bulletSpeed = 20 - 3 * bulletPower;

        // Calcula a direção absoluta do inimigo
        double absBearing = getHeading() + e.getBearing();
        double enemyX = getX() + Math.sin(Math.toRadians(absBearing)) * e.getDistance();
        double enemyY = getY() + Math.cos(Math.toRadians(absBearing)) * e.getDistance();

        // Predição simples: projeta o inimigo alguns ticks à frente
        double deltaTime = e.getDistance() / bulletSpeed;
        double predictedX = enemyX + Math.sin(Math.toRadians(e.getHeading())) * e.getVelocity() * deltaTime;
        double predictedY = enemyY + Math.cos(Math.toRadians(e.getHeading())) * e.getVelocity() * deltaTime;

        // Garante que a previsão não saia dos limites do mapa
        predictedX = Math.max(18.0, Math.min(getBattleFieldWidth() - 18.0, predictedX));
        predictedY = Math.max(18.0, Math.min(getBattleFieldHeight() - 18.0, predictedY));

        // Calcula o ângulo da mira até o ponto previsto
        double theta = Math.toDegrees(Math.atan2(predictedX - getX(), predictedY - getY()));
        double gunTurn = normalizeBearing(theta - getGunHeading());
        setTurnGunRight(gunTurn);

        // Dispara quando estiver alinhado
        if (Math.abs(getGunTurnRemaining()) < 10 && getGunHeat() == 0) {
            fire(bulletPower);
        }

        execute();
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Ao ser atingido, muda direção para confundir a mira inimiga
        moveDirection *= -1;
        setTurnRight(normalizeBearing(90 - e.getBearing()));
        setAhead(100 * moveDirection);
    }

    public void onHitWall(HitWallEvent e) {
        // Sai da parede e muda direção
        moveDirection *= -1;
        back(100);
    }

    // Função utilitária para normalizar ângulos
    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
