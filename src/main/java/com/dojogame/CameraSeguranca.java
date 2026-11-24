package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;

/** Câmera de segurança: alterna entre pausa e vigilância vermelha. */
public final class CameraSeguranca extends Pane {
    private final Point2D direcao;
    private final double alcance;
    private final Polygon cone = new Polygon();
    private final Circle lente = new Circle(0, 0, 12);
    private final ImageView sprite;
    private final Label alerta = new Label("!");
    private double tempo;
    private boolean ativa;

    public CameraSeguranca(Point2D posicao, Point2D direcao) {
        this.direcao = direcao.normalize();
        this.alcance = 210;
        lente.setFill(Color.web("#30251f"));
        lente.setStroke(Color.web("#b88a4c"));
        lente.setStrokeWidth(3);
        alerta.setStyle("-fx-text-fill: #ff2929; -fx-font-size: 28px;"
                + "-fx-font-weight: bold;");
        alerta.setLayoutX(-5);
        alerta.setLayoutY(-48);
        alerta.setVisible(false);
        cone.setMouseTransparent(true);
        var enderecoSprite = getClass().getResource(
                "/sprites/camera_seguranca.png"
        );
        if (enderecoSprite == null) {
            throw new IllegalStateException("Sprite da câmera não encontrado.");
        }
        Image folha = new Image(enderecoSprite.toExternalForm());
        sprite = new ImageView(folha);
        sprite.setFitWidth(62);
        sprite.setFitHeight(62);
        sprite.setPreserveRatio(true);
        sprite.setX(-31);
        sprite.setY(-31);
        sprite.setMouseTransparent(true);
        atualizarQuadroSprite();
        lente.setVisible(false);
        getChildren().addAll(cone, lente, sprite, alerta);
        setLayoutX(posicao.getX());
        setLayoutY(posicao.getY());
        setManaged(false);
        setMouseTransparent(true);
        atualizar(0);
    }

    public void atualizar(double decorrido) {
        tempo += decorrido;
        ativa = ((int) (tempo / 1.15)) % 2 == 0;
        Color cor = ativa ? Color.rgb(255, 42, 36) : Color.rgb(105, 135, 145);
        lente.setFill(ativa ? Color.web("#b71c1c") : Color.web("#34454f"));
        sprite.setOpacity(ativa ? 1.0 : 0.62);
        lente.setEffect(null);
        cone.setFill(Color.rgb((int) (cor.getRed() * 255),
                (int) (cor.getGreen() * 255), (int) (cor.getBlue() * 255),
                ativa ? 0.16 : 0.06));
        cone.setStroke(Color.rgb((int) (cor.getRed() * 255),
                (int) (cor.getGreen() * 255), (int) (cor.getBlue() * 255),
                ativa ? 0.58 : 0.20));
        cone.getPoints().setAll(
                0.0, 0.0,
                rotacionar(direcao, -30).multiply(alcance).getX(),
                rotacionar(direcao, -30).multiply(alcance).getY(),
                rotacionar(direcao, 30).multiply(alcance).getX(),
                rotacionar(direcao, 30).multiply(alcance).getY()
        );
    }

    /** Detecta o centro do jogador somente durante o pulso vermelho. */
    public boolean detectar(Point2D pontoJogador) {
        if (!ativa) {
            alerta.setVisible(false);
            return false;
        }
        Point2D ateJogador = pontoJogador.subtract(getLayoutX(), getLayoutY());
        if (ateJogador.magnitude() > alcance || ateJogador.magnitude() < 1) {
            alerta.setVisible(false);
            return false;
        }
        double angulo = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1,
                direcao.dotProduct(ateJogador.normalize())))));
        boolean encontrou = angulo <= 30;
        alerta.setVisible(encontrou);
        return encontrou;
    }

    private Point2D rotacionar(Point2D ponto, double graus) {
        double angulo = Math.toRadians(graus);
        double cos = Math.cos(angulo);
        double seno = Math.sin(angulo);
        return new Point2D(
                ponto.getX() * cos - ponto.getY() * seno,
                ponto.getX() * seno + ponto.getY() * cos
        );
    }

    private void atualizarQuadroSprite() {
        int quadro = direcao.getY() > 0 ? 0
                : direcao.getX() < 0 ? 1
                : direcao.getX() > 0 ? 2 : 3;
        Image folha = sprite.getImage();
        double largura = folha.getWidth() / 4.0;
        sprite.setViewport(new javafx.geometry.Rectangle2D(
                quadro * largura, 0, largura, folha.getHeight()
        ));
    }
}
