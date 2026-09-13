package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** Primeiro subchefe: persegue, golpeia e executa uma investida. */
public class SubchefeSamurai extends Pane {

    public enum TipoItem {
        SHURIKEN,
        BOMBA_FUMACA,
        KATANA
    }

    public enum TipoAtaqueDistancia {
        SHURIKEN,
        BOMBA
    }

    private static final int VIDA_MAXIMA = 12;
    private static final double RAIO_CORPO = 20.0;
    private static final double RAIO_IMPACTO = 96.0;

    private final Circle corpo;
    private final Circle auraInvestida;
    private final Circle areaImpacto;
    private final Group visual;
    private int vida = VIDA_MAXIMA;
    private double tempoAtaque;
    private double tempoNovaInvestida = 2.4;
    private double tempoPreparandoInvestida;
    private double tempoInvestida;
    private double tempoNovoSalto = 1.8;
    private double tempoPreparandoSalto;
    private double tempoEfeitoImpacto;
    private double tempoNovoArremesso = 3.2;
    private double tempoPreparandoArremesso;
    private Point2D direcaoInvestida = Point2D.ZERO;
    private TipoItem itemDerrubado;
    private TipoAtaqueDistancia ataqueDistanciaPendente;
    private int investidasConcluidas;
    private boolean proximoArremessoEhBomba;
    private boolean reforcosChamados;
    private boolean reforcosPendentes;

    public SubchefeSamurai(Point2D posicaoInicial) {
        areaImpacto = new Circle(0, 0, RAIO_IMPACTO);
        areaImpacto.setFill(Color.rgb(225, 40, 25, 0.10));
        areaImpacto.setStroke(Color.rgb(255, 105, 45, 0.78));
        areaImpacto.setStrokeWidth(4);
        areaImpacto.getStrokeDashArray().addAll(10.0, 7.0);
        areaImpacto.setVisible(false);

        auraInvestida = new Circle(0, 0, 28, Color.TRANSPARENT);
        auraInvestida.setStroke(Color.rgb(255, 70, 40, 0.75));
        auraInvestida.setStrokeWidth(4);
        auraInvestida.setVisible(false);

        corpo = new Circle(0, 0, RAIO_CORPO, Color.web("#6f1d1b"));
        corpo.setStroke(Color.web("#1b1010"));
        corpo.setStrokeWidth(4);

        Rectangle armadura = new Rectangle(-14, -15, 28, 22);
        armadura.setArcWidth(8);
        armadura.setArcHeight(8);
        armadura.setFill(Color.web("#262a30"));
        armadura.setStroke(Color.web("#b28a45"));

        Line katana = new Line(8, -4, 31, 10);
        katana.setStroke(Color.SILVER);
        katana.setStrokeWidth(4);

        visual = new Group(auraInvestida, corpo, armadura, katana);
        getChildren().addAll(areaImpacto, visual);
        setLayoutX(posicaoInicial.getX());
        setLayoutY(posicaoInicial.getY());
        setManaged(false);
        setMouseTransparent(true);
    }

    /** Atualiza o inimigo e retorna o dano causado neste quadro. */
    public int atualizar(
            double tempo,
            Rectangle jogador,
            CarregadorMapa mapa,
            boolean jogadorOculto
    ) {
        if (estaDerrotado()) {
            return 0;
        }

        tempoAtaque = Math.max(0, tempoAtaque - tempo);
        tempoNovaInvestida -= tempo;
        tempoNovoSalto -= tempo;
        tempoNovoArremesso -= tempo;
        atualizarEfeitoImpacto(tempo);

        Point2D alvo = new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );
        Point2D ateJogador = alvo.subtract(obterPosicao());

        // Durante a investida o samurai segue somente a direção travada
        // durante a preparação, sem corrigir a trajetória no meio do golpe.
        if (tempoInvestida > 0) {
            tempoInvestida -= tempo;
            mover(direcaoInvestida, velocidadeInvestida() * tempo, mapa);
            auraInvestida.setVisible(true);
            auraInvestida.setRadius(34);
            auraInvestida.setFill(Color.rgb(255, 55, 30, 0.18));
            auraInvestida.setStroke(Color.rgb(255, 55, 30, 0.90));

            if (tempoInvestida <= 0) {
                finalizarInvestida();
            }

            Point2D distanciaAtual = alvo.subtract(obterPosicao());

            if (!jogadorOculto
                    && distanciaAtual.magnitude() <= 43
                    && tempoAtaque <= 0) {
                tempoAtaque = 0.8;
                return segundaFase() ? 22 : 18;
            }

            return 0;
        }

        // Antes de avançar, o chefe para, aponta e carrega o golpe.
        if (tempoPreparandoInvestida > 0) {
            tempoPreparandoInvestida -= tempo;
            auraInvestida.setVisible(true);
            double progresso = 1.0
                    - Math.max(0, tempoPreparandoInvestida) / 0.72;
            auraInvestida.setRadius(28 + progresso * 12);
            auraInvestida.setFill(Color.rgb(255, 190, 35, 0.12));
            auraInvestida.setStroke(Color.rgb(255, 205, 60, 0.92));

            if (tempoPreparandoInvestida <= 0) {
                tempoInvestida = segundaFase() ? 0.54 : 0.46;
            }

            return 0;
        }

        // O salto mostra antecipadamente a área que sofrerá o impacto.
        if (tempoPreparandoSalto > 0) {
            tempoPreparandoSalto -= tempo;
            double progresso = 1.0
                    - Math.max(0, tempoPreparandoSalto) / 0.75;
            visual.setTranslateY(-30 * Math.sin(progresso * Math.PI));
            areaImpacto.setVisible(true);
            areaImpacto.setRadius(54 + progresso * (RAIO_IMPACTO - 54));
            areaImpacto.setFill(Color.rgb(255, 165, 35, 0.10));
            areaImpacto.setStroke(Color.rgb(255, 175, 45, 0.88));

            if (tempoPreparandoSalto <= 0) {
                visual.setTranslateY(0);
                tempoAtaque = 0.9;
                tempoEfeitoImpacto = 0.30;
                areaImpacto.setRadius(RAIO_IMPACTO);
                areaImpacto.setFill(Color.rgb(230, 35, 20, 0.28));
                areaImpacto.setStroke(Color.rgb(255, 70, 35, 0.95));

                if (!jogadorOculto
                        && alvo.distance(obterPosicao()) <= RAIO_IMPACTO) {
                    return segundaFase() ? 32 : 26;
                }
            }

            return 0;
        }

        // O arremesso também possui uma pausa curta, evitando ataques sem aviso.
        if (tempoPreparandoArremesso > 0) {
            tempoPreparandoArremesso -= tempo;
            corpo.setStroke(Color.web("#d6b45f"));

            if (tempoPreparandoArremesso <= 0) {
                ataqueDistanciaPendente = proximoArremessoEhBomba
                        ? TipoAtaqueDistancia.BOMBA
                        : TipoAtaqueDistancia.SHURIKEN;
                proximoArremessoEhBomba = !proximoArremessoEhBomba;
                corpo.setStroke(Color.web("#1b1010"));
            }

            return 0;
        }

        auraInvestida.setVisible(false);
        visual.setTranslateY(0);

        if (jogadorOculto) {
            return 0;
        }

        double distancia = ateJogador.magnitude();

        if (distancia <= 118 && tempoNovoSalto <= 0) {
            tempoPreparandoSalto = 0.75;
            tempoNovoSalto = segundaFase() ? 3.4 : 4.2;
            areaImpacto.setVisible(true);
            return 0;
        }

        if (distancia >= 135
                && distancia <= 480
                && tempoNovoArremesso <= 0) {
            apontarPara(ateJogador);
            tempoPreparandoArremesso = 0.45;
            tempoNovoArremesso = segundaFase() ? 3.7 : 4.8;
            return 0;
        }

        if (tempoNovaInvestida <= 0 && distancia <= 390) {
            direcaoInvestida = distancia == 0
                    ? new Point2D(1, 0)
                    : ateJogador.normalize();
            apontarPara(direcaoInvestida);
            tempoPreparandoInvestida = 0.72;
            tempoNovaInvestida = segundaFase() ? 2.5 : 3.3;
            return 0;
        }

        if (distancia > 46) {
            mover(ateJogador, velocidadePerseguicao() * tempo, mapa);
        }

        if (distancia <= 45 && tempoAtaque <= 0) {
            tempoAtaque = segundaFase() ? 0.65 : 0.9;
            return segundaFase() ? 17 : 12;
        }

        return 0;
    }

    public boolean receberGolpe(Rectangle jogador) {
        Point2D centroJogador = new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );

        if (centroJogador.distance(obterPosicao()) > 68) {
            return false;
        }

        receberDano(1);
        return true;
    }

    public void receberDano(int quantidade) {
        vida = Math.max(0, vida - Math.max(0, quantidade));

        if (!reforcosChamados && vida > 0 && vida <= 5) {
            reforcosChamados = true;
            reforcosPendentes = true;
        }

        corpo.setFill(segundaFase()
                ? Color.web("#b3261e")
                : Color.web("#6f1d1b"));
    }

    /** Retorna uma única queda pendente e limpa o evento. */
    public TipoItem consumirItemDerrubado() {
        TipoItem item = itemDerrubado;
        itemDerrubado = null;
        return item;
    }

    public TipoAtaqueDistancia consumirAtaqueDistancia() {
        TipoAtaqueDistancia ataque = ataqueDistanciaPendente;
        ataqueDistanciaPendente = null;
        return ataque;
    }

    /** Informa uma única vez que a vida chegou ao limite dos reforços. */
    public boolean consumirChamadoReforcos() {
        boolean deveChamar = reforcosPendentes;
        reforcosPendentes = false;
        return deveChamar;
    }

    public int obterVida() {
        return vida;
    }

    public int obterVidaMaxima() {
        return VIDA_MAXIMA;
    }

    public boolean estaDerrotado() {
        return vida <= 0;
    }

    public Point2D obterPosicao() {
        return new Point2D(getLayoutX(), getLayoutY());
    }

    private boolean segundaFase() {
        return vida <= VIDA_MAXIMA / 2;
    }

    private double velocidadePerseguicao() {
        return segundaFase() ? 112.0 : 82.0;
    }

    private double velocidadeInvestida() {
        return segundaFase() ? 360.0 : 285.0;
    }

    private void finalizarInvestida() {
        auraInvestida.setVisible(false);
        investidasConcluidas++;

        // A segunda investida sempre libera a katana. Depois disso,
        // recursos comuns aparecem apenas a cada três investidas.
        if (investidasConcluidas == 2) {
            itemDerrubado = TipoItem.KATANA;
        } else if (investidasConcluidas >= 4
                && (investidasConcluidas - 1) % 3 == 0) {
            int numeroQuedaComum = (investidasConcluidas - 1) / 3;
            itemDerrubado = numeroQuedaComum % 2 == 1
                    ? TipoItem.SHURIKEN
                    : TipoItem.BOMBA_FUMACA;
        }
    }

    private void atualizarEfeitoImpacto(double tempo) {
        if (tempoEfeitoImpacto <= 0) {
            if (tempoPreparandoSalto <= 0) {
                areaImpacto.setVisible(false);
            }
            return;
        }

        tempoEfeitoImpacto -= tempo;

        if (tempoEfeitoImpacto <= 0) {
            areaImpacto.setVisible(false);
        }
    }

    private void apontarPara(Point2D direcao) {
        if (direcao.magnitude() == 0) {
            return;
        }

        Point2D normalizada = direcao.normalize();
        visual.setRotate(Math.toDegrees(Math.atan2(
                normalizada.getY(), normalizada.getX()
        )));
    }

    private void mover(
            Point2D direcao,
            double distancia,
            CarregadorMapa mapa
    ) {
        if (direcao.magnitude() == 0) {
            return;
        }

        Point2D normalizada = direcao.normalize();
        apontarPara(normalizada);
        double novoX = getLayoutX() + normalizada.getX() * distancia;
        double novoY = getLayoutY() + normalizada.getY() * distancia;

        if (!mapa.possuiColisao(
                novoX - RAIO_CORPO,
                getLayoutY() - RAIO_CORPO,
                RAIO_CORPO * 2,
                RAIO_CORPO * 2
        )) {
            setLayoutX(novoX);
        }

        if (!mapa.possuiColisao(
                getLayoutX() - RAIO_CORPO,
                novoY - RAIO_CORPO,
                RAIO_CORPO * 2,
                RAIO_CORPO * 2
        )) {
            setLayoutY(novoY);
        }
    }
}
