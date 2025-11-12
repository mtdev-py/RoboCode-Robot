package tank; // Define o pacote (pasta lógica) onde o robô está localizado

import robocode.*;        // Importa as classes principais do Robocode
import java.awt.*;        // Importa classes para cores e gráficos
import java.util.Random;  // Importa a classe para gerar valores aleatórios
import java.awt.geom.Point2D; // Importa Point2D para cálculos de distância

// Declaração da classe principal do robô, herdando de AdvancedRobot (robô com controle avançado)
public class TankSupremo extends AdvancedRobot {

    // Variáveis para armazenar informações sobre o inimigo detectado
    private double enemyBearing;   // Ângulo entre o robô e o inimigo
    private double enemyDistance;  // Distância até o inimigo
    private double enemyHeading;   // Direção em que o inimigo está se movendo
    private double enemyVelocity;  // Velocidade atual do inimigo
    private double previousEnergy = 100; // Energia anterior do inimigo (usada para detectar disparos)
    private Random random = new Random(); // Objeto para gerar números aleatórios

    // Método principal — executado quando o robô é iniciado
    public void run() {
        setColors(Color.BLACK, Color.RED, Color.YELLOW); // Define as cores do corpo, arma e radar
        setAdjustGunForRobotTurn(true);  // Permite que a arma se mova independentemente do corpo
        setAdjustRadarForGunTurn(true);  // Permite que o radar se mova independentemente da arma

        // Loop infinito — mantém o robô ativo durante a batalha
        while (true) {
            turnRadarRight(360); // Gira o radar 360° continuamente para procurar inimigos
        }
    }

    // Evento acionado sempre que o radar detecta outro robô
    public void onScannedRobot(ScannedRobotEvent e) {
        // Armazena as informações do inimigo detectado
        enemyBearing = e.getBearing();
        enemyDistance = e.getDistance();
        enemyHeading = e.getHeading();
        enemyVelocity = e.getVelocity();

        // -----------------------------
        // 🔫 Mira preditiva
        // -----------------------------
        double bulletPower = Math.min(3.0, getEnergy() / 20); // Define a força do tiro com base na energia atual (máx. 3)
        double myX = getX();  // Posição X do robô
        double myY = getY();  // Posição Y do robô
        double absoluteBearing = getHeading() + e.getBearing(); // Direção absoluta até o inimigo

        // Calcula a posição atual do inimigo no campo
        double enemyX = myX + Math.sin(Math.toRadians(absoluteBearing)) * e.getDistance();
        double enemyY = myY + Math.cos(Math.toRadians(absoluteBearing)) * e.getDistance();

        double deltaTime = 0;  // Tempo estimado até o impacto
        double predictedX = enemyX, predictedY = enemyY; // Coordenadas previstas do inimigo

        // Loop para prever onde o inimigo estará quando o tiro chegar
        while ((++deltaTime) * (20.0 - 3.0 * bulletPower) <
               Point2D.distance(myX, myY, predictedX, predictedY)) {
            predictedX += Math.sin(Math.toRadians(enemyHeading)) * enemyVelocity;
            predictedY += Math.cos(Math.toRadians(enemyHeading)) * enemyVelocity;

            // Se o inimigo se aproximar das bordas do mapa, interrompe a previsão
            if (predictedX < 18.0 || predictedY < 18.0 ||
                predictedX > getBattleFieldWidth() - 18.0 ||
                predictedY > getBattleFieldHeight() - 18.0) {

                // Garante que as coordenadas previstas fiquem dentro dos limites do campo
                predictedX = Math.min(Math.max(18.0, predictedX), getBattleFieldWidth() - 18.0);
                predictedY = Math.min(Math.max(18.0, predictedY), getBattleFieldHeight() - 18.0);
                break;
            }
        }

        // Calcula o ângulo necessário para mirar na posição prevista do inimigo
        double theta = Math.toDegrees(Math.atan2(predictedX - myX, predictedY - myY));

        // Gira a arma até o alvo previsto
        setTurnGunRight(normalizeBearing(theta - getGunHeading()));

        // Dispara se a arma estiver pronta e alinhada
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
            fire(bulletPower);
        }

        // -----------------------------
        // 🛡️ Movimentação e defesa
        // -----------------------------
        if (enemyDistance < 200) {
            setBack(100 + random.nextInt(50)); // recua se estiver muito perto
        } else {
            setAhead(150 + random.nextInt(100)); // avança de forma variável
        }

        // Movimento circular imprevisível (dificulta acertos)
        setTurnRight(e.getBearing() + 90 - 30 * (random.nextDouble() - 0.5));

        // Mantém o radar travado no inimigo
        double radarTurn = getHeading() - getRadarHeading() + e.getBearing();
        setTurnRadarRight(2 * normalizeBearing(radarTurn));

        // Estratégia de sobrevivência
        if (getEnergy() < 30 && enemyDistance > 150) {
            setBack(100); // recua se estiver fraco e o inimigo longe
        }
    }

    // Evento acionado quando o robô é atingido por uma bala
    public void onHitByBullet(HitByBulletEvent e) {
        // Movimento evasivo aleatório para confundir o inimigo
        setTurnRight(90 - e.getBearing() + (random.nextDouble() * 90 - 45));
        setAhead(100 + random.nextInt(100));
    }

    // Evento acionado quando o robô colide com a parede
    public void onHitWall(HitWallEvent e) {
        setBack(50);       // Recuar um pouco
        setTurnRight(90);  // Girar para mudar de direção
    }

    // Função auxiliar que mantém os ângulos dentro do intervalo [-180°, 180°]
    private double normalizeBearing(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
