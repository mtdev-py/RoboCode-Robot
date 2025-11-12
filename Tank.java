package tank;

import robocode.*;
import robocode.util.Utils;
import java.awt.Color;

/**
 * Tank - versão melhorada com:
 * 1) mira preditiva,
 * 2) movimento evasivo (circling + inversões aleatórias),
 * 3) estratégia de "recuperação" (conservação de energia).
 *
 * Mantive o mesmo nome de classe para substituir diretamente.
 */
public class Tank extends AdvancedRobot {

    // ----- Parâmetros de comportamento (ajustáveis) -----
    private int moveDirection = 1; // direção do movimento: 1 = frente, -1 = trás (inverte para evasão)
    private long lastReverseTime = 0; // para controlar inversões periódicas
    private static final long REVERSE_INTERVAL = 1000; // ms aproximados entre possíveis inversões
    private double enemyBearingFromBody = 0; // guarda último bearing do inimigo relativo ao nosso heading
    private double enemyDistance = 0; // guarda última distância ao inimigo
    private double enemyEnergy = 100; // última energia conhecida do inimigo
    private boolean conserving = false; // modo de conservação/recuperação (simula "cura")
    private long conserveStart = 0;
    private static final long CONSERVE_DURATION = 3000; // tempo para ficar em modo conservação (ms)

    /**
     * run(): loop principal. Configura cores e parâmetros,
     * e usa set* (controle não bloqueante) + execute() para ações contínuas.
     */
    public void run() {
        // --- Configuração visual ---
        setBodyColor(new Color(30, 0, 0));
        setGunColor(new Color(255, 30, 30));
        setRadarColor(new Color(255, 200, 0));
        setBulletColor(Color.RED);
        setScanColor(Color.ORANGE);

        // --- Ajustes para comportamento independente do corpo/gun/radar ---
        // Faz o gun girar independentemente do corpo
        setAdjustGunForRobotTurn(true);
        // Faz o radar girar independente do canhão
        setAdjustRadarForGunTurn(true);

        // Inicializa radar girando continuamente para detectar inimigos
        setTurnRadarRightRadians(Double.POSITIVE_INFINITY);

        // Loop principal: usar set* para movimentos não-bloqueantes e manter o radar girando
        while (true) {
            // Se estamos em modo de conservação (simulação de "cura"), priorizamos recuar e evitar fogo
            if (conserving) {
                long now = System.currentTimeMillis();
                if (now - conserveStart > CONSERVE_DURATION) {
                    // fim do período de conservação
                    conserving = false;
                } else {
                    // enquanto conserva: mantemos distância do campo central e evitamos gastar energia
                    setAhead(50 * moveDirection);
                    setTurnGunRight(10); // mantem gun girando devagar
                    execute();
                    continue; // voltar pro loop sem executar comportamento agressivo
                }
            }

            // Inversão periódica do movimento para tornar a trajetória menos previsível
            long now = System.currentTimeMillis();
            if (now - lastReverseTime > REVERSE_INTERVAL + (long) (Math.random() * 800)) {
                // Inverte direção aleatoriamente (pequena chance)
                if (Math.random() < 0.3) {
                    moveDirection *= -1;
                }
                lastReverseTime = now;
            }

            // Gira radar continuamente para manter lock (usa turnRadarRight com pequena oscilação)
            setTurnRadarRight(60);
            // Avança/recua continuamente conforme moveDirection
            setAhead(100 * moveDirection);

            execute(); // efetiva as ações setadas de forma não-bloqueante
        }
    }

    /**
     * onScannedRobot: comportamento ao detectar um inimigo
     *
     * Funcionalidades adicionadas:
     * - Mira preditiva: tenta calcular onde o inimigo estará quando a bala chegar.
     * - Circling / movimento evasivo: move perpendicularmente ao inimigo para dificultar tiros.
     * - Ajuste de potência de tiro baseado na distância e em nossa energia (economia).
     * - Atualiza info do inimigo (distance, bearing, energy).
     */
    public void onScannedRobot(ScannedRobotEvent e) {
        // --- Atualiza informações do inimigo ---
        enemyDistance = e.getDistance();
        enemyBearingFromBody = e.getBearing(); // relativo ao nosso heading (graus)
        enemyEnergy = e.getEnergy();

        // --- Primeiro: manter o radar travado no inimigo ---
        double absoluteBearing = getHeading() + e.getBearing(); // direção absoluta (graus)
        double radarTurn = Utils.normalRelativeAngleDegrees(absoluteBearing - getRadarHeading());
        // faz um pequeno overshoot para manter lock
        setTurnRadarRight(radarTurn);

        // --- Movimento evasivo (circling) ---
        // Move perpendicularmente ao inimigo para dificultar acerto direto.
        // Se o inimigo estiver muito perto, aumentamos a evasão.
        double perpendicular = absoluteBearing + 90 * moveDirection; // perpendicular em graus
        double turn = Utils.normalRelativeAngleDegrees(perpendicular - getHeading());
        setTurnRight(turn);
        // ajusta velocidade de acordo com a distância (mais rápido se perto)
        double movePower = (enemyDistance < 150) ? 150 : 100;
        setAhead(movePower * moveDirection);

        // --- Mira preditiva (simples linear) ---
        // Calcula posição prevista do inimigo assumindo velocidade constante.
        // Fórmula básica: previsão baseada na velocidade e heading do inimigo.
        double enemyHeading = getHeading() + e.getHeading(); // heading absoluto do inimigo (deg)
        double enemyVelocity = e.getVelocity();

        // Convert degrees -> radians para seno/cosseno
        double enemyHeadingRad = Math.toRadians(enemyHeading);

        // Estimativa simples do tempo da bala até atingir o inimigo:
        // power -> velocidade da bala: v = 20 - 3 * power (pela documentação do Robocode)
        // aqui escolhemos power baseado na distância e na nossa energia
        double power = chooseFirePower(enemyDistance);

        double bulletSpeed = 20 - 3 * power;
        if (bulletSpeed <= 0) bulletSpeed = 11; // fallback

        // Estima o tempo até o projétil chegar: time = distance / bulletSpeed
        double time = enemyDistance / bulletSpeed;

        // Previsão da posição deslocada do inimigo (linear)
        double predictedX = getX() + enemyDistance * Math.sin(Math.toRadians(absoluteBearing)) + enemyVelocity * Math.sin(enemyHeadingRad) * time;
        double predictedY = getY() + enemyDistance * Math.cos(Math.toRadians(absoluteBearing)) + enemyVelocity * Math.cos(enemyHeadingRad) * time;

        // Calcula o ângulo para a posição prevista
        double dx = predictedX - getX();
        double dy = predictedY - getY();
        double predictedBearing = Math.toDegrees(Math.atan2(dx, dy)); // atan2(x,y) pra robocode (x = sin)

        // Normaliza diferença entre a direção do canhão e o alvo previsto
        double gunTurn = Utils.normalRelativeAngleDegrees(predictedBearing - getGunHeading());
        setTurnGunRight(gunTurn);

        // Se o canhão está aproximadamente apontado, atira
        if (Math.abs(gunTurn) < 10) { // limiar em graus para atirar
            // Se estamos com pouca energia, moderamos o poder (conservação)
            if (getEnergy() < 15) {
                // ativa modo de conservação se cair abaixo de 15
                conserving = true;
                conserveStart = System.currentTimeMillis();
            } else {
                // dispara com potência calculada
                setFire(power);
            }
        }

        // Se o inimigo atirou (energia caiu), fazemos evasão adicional:
        if (enemyEnergy - e.getEnergy() > 0) { // se detectamos queda na energia do inimigo
            // o inimigo atirou -> damos um reverse imediato para evitar follow-through
            moveDirection *= -1;
        }
    }

    /**
     * onHitByBullet: reação ao ser atingido.
     * - Inverte direçao e realiza evasão rápida.
     * - Tenta contra-atacar com pouco fogo.
     */
    public void onHitByBullet(HitByBulletEvent e) {
        // Inverte a direção de movimento para confundir o atirador
        moveDirection *= -1;

        // Recua um pouco e gira a arma rapidamente para tentar resposta
        setBack(50 * moveDirection);
        setTurnGunRight(90);
        // dispara com baixa potência para não gastar muita energia ao mesmo tempo que revida
        if (!conserving && getEnergy() > 5) {
            setFire(1);
        }
        execute();
    }

    /**
     * onHitWall: reação ao colidir com a parede.
     * - Recuo forte e inverter direção.
     */
    public void onHitWall(HitWallEvent e) {
        // Recuar forte para evitar prender-se na parede e inverter movimento
        moveDirection *= -1;
        setBack(120);
        setTurnRight(90);
        execute();
    }

    /**
     * Escolhe potência de disparo com base na distância e em nossa energia.
     * Explicação:
     * - Mais perto -> power maior (mais dano).
     * - Se nossa energia estiver baixa, reduzimos potência para economizar.
     */
    private double chooseFirePower(double distance) {
        double power;
        if (distance < 150) {
            power = 3;
        } else if (distance < 400) {
            power = 2;
        } else {
            power = 1;
        }

        // Se pouca energia, reduzir potência para conservar
        if (getEnergy() < 20) {
            power = Math.min(power, 1.5);
        }
        // Também limitar para não exceder energia disponível
        power = Math.min(power, getEnergy() - 0.1);
        if (power < 0.1) power = 0.1;
        return power;
    }
}
