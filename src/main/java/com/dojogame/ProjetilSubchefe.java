package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** Shuriken ou bomba lançada pelo primeiro subchefe. */
public class ProjetilSubchefe extends Circle {

    private static final double RAIO_EXPLOSAO = 72.0;

    private final SubchefeSamurai.TipoAtaqueDistancia tipo;
    private final Point2D velocidade;
    private double tempoVoo;
    private double tempoEfeitoExplosao;
    private boolean explodiu;
    private boolean danoAplicado;
    private boolean removido;

    public ProjetilSubchefe(
            Point2D origem,
            Point2D destino,
            SubchefeSamurai.TipoAtaqueDistancia tipo
    ) {
        super(origem.getX(), origem.getY(),
                tipo == SubchefeSamurai.TipoAtaqueDistancia.BOMBA ? 10 : 6);
        this.tipo = tipo;

        Point2D direcao = destino.subtract(origem);

        if (direcao.magnitude() == 0) {
            direcao = new Point2D(1, 0);
        }

        if (tipo == SubchefeSamurai.TipoAtaqueDistancia.BOMBA) {
            tempoVoo = 0.9;
            double velocidadeNecessaria = Math.min(
                    380,
                    direcao.magnitude() / tempoVoo
            );
            velocidade = direcao.normalize().multiply(velocidadeNecessaria);
            setFill(Color.web("#30343b"));
            setStroke(Color.web("#ff8c42"));
            setStrokeWidth(3);
        } else {
            tempoVoo = 2.4;
            velocidade = direcao.normalize().multiply(410);
            setFill(Color.SILVER);
            setStroke(Color.web("#541414"));
            setStrokeWidth(2);
        }

        setManaged(false);
        setMouseTransparent(true);
    }

    /** Atualiza o projétil e retorna o dano causado neste quadro. */
    public int atualizar(
            double tempo,
            Rectangle jogador,
            CarregadorMapa mapa
    ) {
        if (removido) {
            return 0;
        }

        if (explodiu) {
            tempoEfeitoExplosao -= tempo;
            int dano = calcularDanoExplosao(jogador);

            if (tempoEfeitoExplosao <= 0) {
                removido = true;
            }

            return dano;
        }

        setCenterX(getCenterX() + velocidade.getX() * tempo);
        setCenterY(getCenterY() + velocidade.getY() * tempo);
        setRotate(getRotate() + 900 * tempo);
        tempoVoo -= tempo;

        boolean bateuParede = mapa.possuiColisao(
                getCenterX() - getRadius(),
                getCenterY() - getRadius(),
                getRadius() * 2,
                getRadius() * 2
        );

        if (tipo == SubchefeSamurai.TipoAtaqueDistancia.SHURIKEN) {
            if (getBoundsInParent().intersects(
                    jogador.getBoundsInParent()
            )) {
                removido = true;
                return 14;
            }

            if (bateuParede || tempoVoo <= 0) {
                removido = true;
            }

            return 0;
        }

        if (bateuParede
                || tempoVoo <= 0
                || getBoundsInParent().intersects(
                        jogador.getBoundsInParent()
                )) {
            explodir();
            return calcularDanoExplosao(jogador);
        }

        return 0;
    }

    public boolean deveSerRemovido() {
        return removido;
    }

    private void explodir() {
        explodiu = true;
        tempoEfeitoExplosao = 0.34;
        setRadius(RAIO_EXPLOSAO);
        setFill(Color.rgb(235, 65, 25, 0.24));
        setStroke(Color.rgb(255, 155, 45, 0.92));
        setStrokeWidth(4);
    }

    private int calcularDanoExplosao(Rectangle jogador) {
        if (danoAplicado) {
            return 0;
        }

        Point2D centroJogador = new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );

        if (centroJogador.distance(getCenterX(), getCenterY())
                > RAIO_EXPLOSAO) {
            return 0;
        }

        danoAplicado = true;
        return 22;
    }
}
