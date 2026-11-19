package com.dojogame;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.RotateTransition;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/** Guarda provisório com patrulha, visão e perseguição. */
public class GuardaPatrulha extends Pane {

    public enum TipoVisual {
        COMUM,
        DISTANCIA,
        REFORCO
    }

    private static final int VIDA_MAXIMA = 5;

    // Fora do alerta, os guardas deixam mais espaço para infiltração.
    private static final double VELOCIDADE_PATRULHA = 48.0;
    private static final double VELOCIDADE_BUSCA = 72.0;
    private static final double VELOCIDADE_RETORNO = 55.0;

    // Samurais encurtam a distância rapidamente; arqueiros procuram espaço.
    private static final double VELOCIDADE_PERSEGUICAO_SAMURAI = 152.0;
    private static final double VELOCIDADE_PERSEGUICAO_DISTANCIA = 112.0;
    private static final double ALCANCE_VISAO = 190.0;
    private static final double METADE_ANGULO_VISAO = 32.0;
    private static final int QUANTIDADE_RAIOS_VISAO = 32;
    private static final double INTERVALO_DISPARO = 1.0;
    private static final double ALCANCE_MINIMO_DISPARO = 115.0;
    private static final double ALCANCE_MAXIMO_DISPARO = 430.0;
    private static final double ALCANCE_ATAQUE_CORPO_A_CORPO = 45.0;

    private final List<Point2D> rotaPatrulha;
    private final Polygon coneVisao;
    private final Rotate rotacaoVisao = new Rotate(0, 0, 0);
    private final Label exclamacao;
    private final Circle corpo;
    private final ImageView sprite;
    private final Rectangle fundoBarraVida;
    private final Rectangle barraVida;

    private TipoVisual tipoVisual = TipoVisual.COMUM;
    private Image folhaCaminhada;
    private Image folhaAtaque;
    private int quadroSprite;
    private double tempoQuadroSprite;
    private double tempoAtaqueSprite;

    private List<Point2D> caminhoPerseguicao = List.of();
    private int indicePatrulha = 1;
    private int sentidoPatrulha = 1;
    private int indicePerseguicao = 1;
    private boolean emAlerta;
    private boolean retornandoPatrulha;
    private boolean investigando;
    private int indiceRotaRetorno;
    private double tempoAteDisparo;
    private double tempoAteAtaqueCorpoACorpo;
    private Point2D direcaoOlhar = new Point2D(1, 0);
    private int vida = VIDA_MAXIMA;

    public GuardaPatrulha(List<Point2D> rota) {
        if (rota == null || rota.size() < 2) {
            throw new IllegalArgumentException(
                    "A rota do guarda precisa possuir pelo menos dois pontos."
            );
        }

        rotaPatrulha = List.copyOf(rota);

        /*
         * O cone é um polígono reconstruído por pequenos raios. Isso permite
         * que cada trecho pare exatamente ao encontrar uma árvore ou parede.
         */
        coneVisao = new Polygon();
        coneVisao.setFill(Color.rgb(255, 220, 70, 0.14));
        coneVisao.setStroke(Color.rgb(255, 220, 70, 0.55));
        coneVisao.setMouseTransparent(true);

        Line indicadorDirecao = new Line(0, 0, 17, 0);
        indicadorDirecao.setStroke(Color.rgb(70, 35, 10));
        indicadorDirecao.setStrokeWidth(3);

        Group visualDaDirecao = new Group(coneVisao, indicadorDirecao);
        visualDaDirecao.getTransforms().add(rotacaoVisao);
        visualDaDirecao.setMouseTransparent(true);

        corpo = new Circle(0, 0, 11, Color.DARKORANGE);
        corpo.setStroke(Color.rgb(35, 24, 15));
        corpo.setStrokeWidth(2);
        corpo.setVisible(false);

        indicadorDirecao.setVisible(false);
        folhaCaminhada = carregarImagem(
                "/sprites/guarda_comum_caminhada_alinhado.png"
        );
        folhaAtaque = carregarImagem(
                "/sprites/guarda_comum_ataque_alinhado.png"
        );
        sprite = new ImageView(folhaCaminhada);
        sprite.setFitWidth(67.5);
        sprite.setFitHeight(60);
        sprite.setPreserveRatio(true);
        sprite.setSmooth(true);
        sprite.setX(-33.75);
        sprite.setY(-45);
        sprite.setMouseTransparent(true);
        definirQuadroSprite(2, 0);

        exclamacao = new Label("!");
        exclamacao.setStyle(
                "-fx-text-fill: #ff2020;"
                        + "-fx-font-size: 25px;"
                        + "-fx-font-weight: bold;"
        );
        exclamacao.setLayoutX(-5);
        exclamacao.setLayoutY(-39);
        exclamacao.setVisible(false);
        exclamacao.setMouseTransparent(true);

        fundoBarraVida = new Rectangle(-20, -52, 40, 6);
        fundoBarraVida.setArcWidth(4);
        fundoBarraVida.setArcHeight(4);
        fundoBarraVida.setFill(Color.rgb(25, 12, 12, 0.88));
        fundoBarraVida.setStroke(Color.rgb(15, 8, 8, 0.95));
        fundoBarraVida.setStrokeWidth(1);
        fundoBarraVida.setMouseTransparent(true);

        barraVida = new Rectangle(-19, -51, 38, 4);
        barraVida.setArcWidth(3);
        barraVida.setArcHeight(3);
        barraVida.setFill(Color.web("#55d96b"));
        barraVida.setMouseTransparent(true);

        getChildren().addAll(
                visualDaDirecao,
                corpo,
                sprite,
                fundoBarraVida,
                barraVida,
                exclamacao
        );
        setManaged(false);
        setMouseTransparent(true);

        Point2D inicio = rotaPatrulha.get(0);
        setLayoutX(inicio.getX());
        setLayoutY(inicio.getY());
        atualizarDirecao(rotaPatrulha.get(1).subtract(inicio));
    }

    public void definirTipoVisual(TipoVisual tipo) {
        tipoVisual = tipo == null ? TipoVisual.COMUM : tipo;

        String prefixo = switch (tipoVisual) {
            case COMUM -> "guarda_comum";
            case DISTANCIA -> "guarda_distancia";
            case REFORCO -> "guarda_reforco";
        };
        folhaCaminhada = carregarImagem(
                "/sprites/" + prefixo + "_caminhada_alinhado.png"
        );
        folhaAtaque = carregarImagem(
                "/sprites/" + prefixo + "_ataque_alinhado.png"
        );
        sprite.setImage(folhaCaminhada);

        double altura = tipoVisual == TipoVisual.REFORCO ? 68 : 60;
        double largura = altura * 1.125;
        sprite.setFitWidth(largura);
        sprite.setFitHeight(altura);
        sprite.setX(-largura / 2.0);
        sprite.setY(-altura * 0.75);
        quadroSprite = 0;
        tempoQuadroSprite = 0;
        definirQuadroSprite(linhaSpriteAtual(), 0);
    }

    private Image carregarImagem(String caminho) {
        var endereco = getClass().getResource(caminho);

        if (endereco == null) {
            throw new IllegalStateException(
                    "Sprite do guarda não encontrado: " + caminho
            );
        }
        return new Image(endereco.toExternalForm());
    }

    public void atualizarPatrulha(double tempoDecorrido) {
        if (emAlerta || retornandoPatrulha || investigando) {
            return;
        }

        atualizarAnimacao(tempoDecorrido, true);

        double distanciaRestante = VELOCIDADE_PATRULHA * tempoDecorrido;

        while (distanciaRestante > 0) {
            Point2D destino = rotaPatrulha.get(indicePatrulha);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return;
            }

            distanciaRestante = sobra;
            escolherProximoDestinoPatrulha();
        }
    }

    public void ativarAlerta() {
        emAlerta = true;
        retornandoPatrulha = false;
        investigando = false;
        exclamacao.setText("!");
        exclamacao.setStyle(
                "-fx-text-fill: #ff2020;"
                        + "-fx-font-size: 25px;"
                        + "-fx-font-weight: bold;"
        );
        exclamacao.setVisible(true);
        coneVisao.setFill(Color.rgb(255, 40, 40, 0.12));
        coneVisao.setStroke(Color.rgb(255, 40, 40, 0.50));
        tempoAteDisparo = 0;
        tempoAteAtaqueCorpoACorpo = 0;
    }

    /** Faz somente este guarda investigar o ponto onde ouviu um ruído. */
    public void iniciarInvestigacao(List<Point2D> caminho) {
        emAlerta = false;
        retornandoPatrulha = false;
        investigando = true;
        caminhoPerseguicao = List.copyOf(caminho);
        indicePerseguicao = Math.min(1, caminhoPerseguicao.size());
        exclamacao.setText("?");
        exclamacao.setStyle(
                "-fx-text-fill: #ffd34d;"
                        + "-fx-font-size: 25px;"
                        + "-fx-font-weight: bold;"
        );
        exclamacao.setVisible(true);
        coneVisao.setFill(Color.rgb(255, 190, 45, 0.15));
        coneVisao.setStroke(Color.rgb(255, 190, 45, 0.65));
    }

    /** Retorna verdadeiro ao alcançar o último local de barulho. */
    public boolean atualizarInvestigacao(double tempoDecorrido) {
        if (!investigando) {
            return false;
        }

        boolean chegou = indicePerseguicao >= caminhoPerseguicao.size();
        atualizarAnimacao(tempoDecorrido, !chegou);
        if (chegou) {
            return true;
        }
        return atualizarCaminhoAtual(tempoDecorrido, VELOCIDADE_BUSCA);
    }

    public boolean estaInvestigando() {
        return investigando;
    }

    /** Recebe um caminho atualizado em direção ao redor do jogador. */
    public void definirCaminhoPerseguicao(List<Point2D> caminho) {
        if ((caminho == null || caminho.size() <= 1)
                && indicePerseguicao < caminhoPerseguicao.size()) {
            // Uma falha momentânea de planejamento não apaga uma rota válida.
            return;
        }
        caminhoPerseguicao = List.copyOf(caminho);
        indicePerseguicao = Math.min(1, caminhoPerseguicao.size());
    }

    public void atualizarPerseguicao(
            double tempoDecorrido,
            boolean jogadorVisivel
    ) {
        if (!emAlerta) {
            return;
        }

        atualizarAnimacao(tempoDecorrido, true);

        double velocidade = jogadorVisivel
                ? velocidadePerseguicao()
                : VELOCIDADE_BUSCA;
        atualizarCaminhoAtual(tempoDecorrido, velocidade);
    }

    /** Distância que cada tipo tenta manter do jogador ao recalcular o A*. */
    public double obterDistanciaCombatePreferida() {
        // 165 fica dentro do cone de visão (190) e fora do alcance corpo a corpo.
        return tipoVisual == TipoVisual.DISTANCIA ? 165.0 : 24.0;
    }

    public boolean ehGuardaDistancia() {
        return tipoVisual == TipoVisual.DISTANCIA;
    }

    /**
     * Faz o guarda voltar pelo mapa até o ponto mais próximo de sua rota.
     */
    public void iniciarRetornoPatrulha(
            List<Point2D> caminho,
            Point2D pontoDaRota
    ) {
        emAlerta = false;
        retornandoPatrulha = true;
        investigando = false;
        exclamacao.setVisible(false);
        coneVisao.setFill(Color.rgb(255, 220, 70, 0.14));
        coneVisao.setStroke(Color.rgb(255, 220, 70, 0.55));
        caminhoPerseguicao = List.copyOf(caminho);
        indicePerseguicao = Math.min(1, caminhoPerseguicao.size());
        indiceRotaRetorno = Math.max(0, rotaPatrulha.indexOf(pontoDaRota));
    }

    public void atualizarRetornoPatrulha(double tempoDecorrido) {
        if (!retornandoPatrulha) {
            return;
        }

        atualizarAnimacao(tempoDecorrido, true);

        boolean terminou = atualizarCaminhoAtual(
                tempoDecorrido,
                VELOCIDADE_RETORNO
        );

        if (terminou) {
            retornandoPatrulha = false;
            indicePatrulha = indiceRotaRetorno;
        }
    }

    public boolean estaRetornandoPatrulha() {
        return retornandoPatrulha;
    }

    /**
     * Expõe uma cópia do trajeto atual para o painel de depuração.
     * Durante a patrulha, mostra somente o próximo trecho da rota manual.
     */
    public List<Point2D> obterCaminhoParaDepuracao() {
        List<Point2D> caminho = new ArrayList<>();
        caminho.add(obterPosicao());

        if ((emAlerta || retornandoPatrulha || investigando)
                && indicePerseguicao < caminhoPerseguicao.size()) {
            caminho.addAll(caminhoPerseguicao.subList(
                    indicePerseguicao,
                    caminhoPerseguicao.size()
            ));
        } else if (!emAlerta && !retornandoPatrulha && !investigando
                && indicePatrulha < rotaPatrulha.size()) {
            caminho.add(rotaPatrulha.get(indicePatrulha));
        }

        return List.copyOf(caminho);
    }

    public Point2D obterPontoPatrulhaMaisProximo() {
        Point2D maisProximo = rotaPatrulha.get(0);
        double menorDistancia = obterPosicao().distance(maisProximo);

        for (Point2D ponto : rotaPatrulha) {
            double distancia = obterPosicao().distance(ponto);

            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = ponto;
            }
        }

        return maisProximo;
    }

    private boolean atualizarCaminhoAtual(
            double tempoDecorrido,
            double velocidade
    ) {
        if (caminhoPerseguicao.size() <= 1
                || indicePerseguicao >= caminhoPerseguicao.size()) {
            return true;
        }

        double distanciaRestante = velocidade * tempoDecorrido;

        while (distanciaRestante > 0
                && indicePerseguicao < caminhoPerseguicao.size()) {
            Point2D destino = caminhoPerseguicao.get(indicePerseguicao);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return false;
            }

            distanciaRestante = sobra;
            indicePerseguicao++;
        }

        return indicePerseguicao >= caminhoPerseguicao.size();
    }

    public boolean consegueVer(
            Rectangle jogador,
            CarregadorMapa carregadorMapa
    ) {
        // O centro visual precisa entrar no cone. Antes, uma única quina da
        // caixa de colisão já ativava o alerta e parecia uma visão fantasma.
        return pontoEstaVisivel(
                centroDoJogador(jogador),
                carregadorMapa
        );
    }

    /**
     * Reconstrói o cone visível, limitando cada raio pelo primeiro obstáculo.
     */
    public void atualizarCampoVisao(CarregadorMapa carregadorMapa) {
        coneVisao.getPoints().clear();
        coneVisao.getPoints().addAll(0.0, 0.0);

        double anguloDirecao = Math.atan2(
                direcaoOlhar.getY(),
                direcaoOlhar.getX()
        );

        for (int raio = 0; raio <= QUANTIDADE_RAIOS_VISAO; raio++) {
            double proporcao = raio / (double) QUANTIDADE_RAIOS_VISAO;
            double anguloLocalGraus = -METADE_ANGULO_VISAO
                    + proporcao * METADE_ANGULO_VISAO * 2.0;
            double anguloLocal = Math.toRadians(anguloLocalGraus);
            double anguloMundo = anguloDirecao + anguloLocal;

            Point2D direcaoRaio = new Point2D(
                    Math.cos(anguloMundo),
                    Math.sin(anguloMundo)
            );
            double alcanceLivre = carregadorMapa.obterAlcanceLivre(
                    obterPosicao(),
                    direcaoRaio,
                    ALCANCE_VISAO
            );

            /*
             * Os pontos ficam no sistema local do guarda. O grupo aplica a
             * rotação geral, mantendo desenho e cálculo na mesma direção.
             */
            coneVisao.getPoints().addAll(
                    Math.cos(anguloLocal) * alcanceLivre,
                    Math.sin(anguloLocal) * alcanceLivre
            );
        }
    }

    private boolean pontoEstaVisivel(
            Point2D ponto,
            CarregadorMapa carregadorMapa
    ) {
        Point2D atePonto = ponto.subtract(obterPosicao());
        double distancia = atePonto.magnitude();

        if (distancia > ALCANCE_VISAO) {
            return false;
        }

        if (distancia == 0) {
            return true;
        }

        double produtoEscalar = direcaoOlhar.dotProduct(atePonto.normalize());
        double limiteAngulo = Math.cos(Math.toRadians(METADE_ANGULO_VISAO));

        return produtoEscalar >= limiteAngulo
                && carregadorMapa.possuiLinhaDeVisao(
                        obterPosicao(), ponto
                );
    }

    /** Somente o guarda de distância dispara, e nunca à queima-roupa. */
    public boolean podeDisparar(
            Rectangle jogador,
            CarregadorMapa carregadorMapa,
            double tempoDecorrido
    ) {
        if (!emAlerta || tipoVisual != TipoVisual.DISTANCIA) {
            return false;
        }

        tempoAteDisparo -= tempoDecorrido;
        Point2D centroJogador = centroDoJogador(jogador);

        /*
         * Linha reta não é suficiente: o jogador precisa estar dentro do
         * mesmo cone usado pela detecção e exibido na tela. Isso impede que
         * o guarda mire por um campo de visão antigo ou lateral.
         */
        double distancia = obterPosicao().distance(centroJogador);
        if (distancia < ALCANCE_MINIMO_DISPARO
                || distancia > ALCANCE_MAXIMO_DISPARO
                || !consegueVer(jogador, carregadorMapa)) {
            return false;
        }

        atualizarDirecao(centroJogador.subtract(obterPosicao()));

        if (tempoAteDisparo > 0) {
            return false;
        }

        tempoAteDisparo = INTERVALO_DISPARO;
        tempoAtaqueSprite = 0.72;
        tempoQuadroSprite = 0;
        quadroSprite = 0;
        return true;
    }

    /** Samurais e reforços atacam apenas quando alcançam o jogador. */
    public boolean podeAtacarCorpoACorpo(
            Rectangle jogador,
            double tempoDecorrido
    ) {
        if (!emAlerta || tipoVisual == TipoVisual.DISTANCIA) {
            return false;
        }

        tempoAteAtaqueCorpoACorpo -= tempoDecorrido;
        Point2D centroJogador = centroDoJogador(jogador);
        Point2D ateJogador = centroJogador.subtract(obterPosicao());

        if (ateJogador.magnitude() > ALCANCE_ATAQUE_CORPO_A_CORPO) {
            return false;
        }

        atualizarDirecao(ateJogador);
        if (tempoAteAtaqueCorpoACorpo > 0) {
            return false;
        }

        tempoAteAtaqueCorpoACorpo = tipoVisual == TipoVisual.REFORCO
                ? 0.72 : 0.86;
        tempoAtaqueSprite = 0.56;
        tempoQuadroSprite = 0;
        quadroSprite = 0;
        return true;
    }

    public int obterDanoAtaqueCorpoACorpo() {
        return tipoVisual == TipoVisual.REFORCO ? 16 : 13;
    }

    public Point2D obterPosicao() {
        return new Point2D(getLayoutX(), getLayoutY());
    }

    /** Audição depende apenas da proximidade; obstáculos bloqueiam visão, não som. */
    public boolean consegueOuvir(Point2D origemSom, double alcance) {
        return origemSom != null
                && alcance >= 0
                && obterPosicao().distance(origemSom) <= alcance;
    }

    /** Aplica dano e retorna verdadeiro quando o guarda foi derrotado. */
    public boolean receberDano(int quantidade) {
        vida = Math.max(0, vida - Math.max(0, quantidade));
        double proporcao = vida / (double) VIDA_MAXIMA;
        barraVida.setWidth(38 * proporcao);
        barraVida.setFill(proporcao > 0.55
                ? Color.web("#55d96b")
                : proporcao > 0.25
                ? Color.web("#f0b83f")
                : Color.web("#e4473f"));
        corpo.setFill(Color.color(
                0.55 + proporcao * 0.45,
                0.16 + proporcao * 0.24,
                0.04
        ));

        if (vida > 0) {
            FadeTransition dano = new FadeTransition(
                    Duration.millis(90), sprite
            );
            dano.setFromValue(0.25);
            dano.setToValue(1.0);
            dano.setCycleCount(3);
            dano.setAutoReverse(true);
            dano.playFromStart();
        }
        return vida == 0;
    }

    /** Toca a queda antes de o nó visual ser retirado do mapa. */
    public void reproduzirDerrota(Runnable aoTerminar) {
        coneVisao.setVisible(false);
        exclamacao.setVisible(false);
        fundoBarraVida.setVisible(false);
        barraVida.setVisible(false);

        RotateTransition queda = new RotateTransition(
                Duration.millis(420), sprite
        );
        queda.setToAngle(direcaoOlhar.getX() < 0 ? -88 : 88);
        FadeTransition desaparecer = new FadeTransition(
                Duration.millis(420), this
        );
        desaparecer.setFromValue(1.0);
        desaparecer.setToValue(0.15);

        ParallelTransition derrota = new ParallelTransition(
                queda,
                desaparecer
        );
        derrota.setOnFinished(evento -> {
            if (aoTerminar != null) {
                aoTerminar.run();
            }
        });
        derrota.play();
    }

    public int obterVida() {
        return vida;
    }

    public int obterVidaMaxima() {
        return VIDA_MAXIMA;
    }

    private void atualizarAnimacao(
            double tempoDecorrido,
            boolean movendo
    ) {
        Image folhaAtual = folhaCaminhada;
        double intervalo = emAlerta ? 0.10 : 0.14;

        if (tempoAtaqueSprite > 0) {
            folhaAtual = folhaAtaque;
            intervalo = tipoVisual == TipoVisual.DISTANCIA ? 0.18 : 0.14;
            tempoAtaqueSprite = Math.max(
                    0,
                    tempoAtaqueSprite - tempoDecorrido
            );
        } else if (!movendo) {
            quadroSprite = 0;
            tempoQuadroSprite = 0;
        }

        if (tempoAtaqueSprite > 0 || movendo) {
            tempoQuadroSprite += tempoDecorrido;

            while (tempoQuadroSprite >= intervalo) {
                tempoQuadroSprite -= intervalo;
                quadroSprite = tempoAtaqueSprite > 0
                        ? Math.min(3, quadroSprite + 1)
                        : (quadroSprite + 1) % 4;
            }
        }

        if (tempoAtaqueSprite == 0 && sprite.getImage() == folhaAtaque) {
            folhaAtual = folhaCaminhada;
            quadroSprite = 0;
            tempoQuadroSprite = 0;
        }

        if (sprite.getImage() != folhaAtual) {
            sprite.setImage(folhaAtual);
        }
        definirQuadroSprite(linhaSpriteAtual(), quadroSprite);
    }

    private int linhaSpriteAtual() {
        if (Math.abs(direcaoOlhar.getX())
                > Math.abs(direcaoOlhar.getY())) {
            return direcaoOlhar.getX() < 0 ? 1 : 2;
        }
        return direcaoOlhar.getY() > 0 ? 0 : 3;
    }

    private void definirQuadroSprite(int linha, int coluna) {
        Image folha = sprite.getImage();
        double larguraQuadro = folha.getWidth() / 4.0;
        double alturaQuadro = folha.getHeight() / 4.0;
        sprite.setViewport(new Rectangle2D(
                coluna * larguraQuadro,
                linha * alturaQuadro,
                larguraQuadro,
                alturaQuadro
        ));
    }

    private Point2D centroDoJogador(Rectangle jogador) {
        return new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );
    }

    private double velocidadePerseguicao() {
        return tipoVisual == TipoVisual.DISTANCIA
                ? VELOCIDADE_PERSEGUICAO_DISTANCIA
                : VELOCIDADE_PERSEGUICAO_SAMURAI;
    }

    private double moverEmDirecao(Point2D destino, double distanciaPermitida) {
        Point2D deslocamento = destino.subtract(obterPosicao());
        double distancia = deslocamento.magnitude();

        if (distancia == 0) {
            return distanciaPermitida;
        }

        atualizarDirecao(deslocamento);

        if (distancia <= distanciaPermitida) {
            setLayoutX(destino.getX());
            setLayoutY(destino.getY());
            return distanciaPermitida - distancia;
        }

        Point2D movimento = deslocamento
                .normalize()
                .multiply(distanciaPermitida);
        setLayoutX(getLayoutX() + movimento.getX());
        setLayoutY(getLayoutY() + movimento.getY());
        return -1;
    }

    private void atualizarDirecao(Point2D deslocamento) {
        if (deslocamento.magnitude() == 0) {
            return;
        }

        direcaoOlhar = deslocamento.normalize();
        rotacaoVisao.setAngle(Math.toDegrees(
                Math.atan2(direcaoOlhar.getY(), direcaoOlhar.getX())
        ));
    }

    private void escolherProximoDestinoPatrulha() {
        if (indicePatrulha == rotaPatrulha.size() - 1) {
            sentidoPatrulha = -1;
        } else if (indicePatrulha == 0) {
            sentidoPatrulha = 1;
        }

        indicePatrulha += sentidoPatrulha;

        /*
         * Vira o campo de visão no mesmo quadro em que troca de destino.
         * Sem isso, a direção antiga permanecia ativa por um quadro.
         */
        atualizarDirecao(
                rotaPatrulha.get(indicePatrulha).subtract(obterPosicao())
        );
    }
}
