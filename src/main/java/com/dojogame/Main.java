package com.dojogame;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Classe principal do jogo.
 *
 * Responsável por:
 * - Criar a janela;
 * - Criar o jogador provisório;
 * - Ler as teclas WASD;
 * - Atualizar a movimentação do jogador.
 */
public class Main extends Application {

    // Largura da área visível do jogo em pixels.
    private static final double LARGURA_JANELA = 960.0;

    // Altura da área visível do jogo em pixels.
    private static final double ALTURA_JANELA = 640.0;

    /*
     * Guarda todas as teclas que estão pressionadas.
     *
     * Usamos um conjunto porque o jogador pode pressionar
     * mais de uma tecla ao mesmo tempo, como W + D.
     */
    private final Set<KeyCode> teclasPressionadas = new HashSet<>();

    /*
     * Velocidade do jogador em pixels por segundo.
     *
     * Depois poderemos alterar esse valor ou transferi-lo
     * para uma classe própria chamada Jogador.
     */
    private static final double VELOCIDADE_JOGADOR = 220.0;

    // Tempo escondido necessário para os guardas abandonarem a perseguição.
    private static final double TEMPO_PARA_PERDER_JOGADOR = 3.0;

    private String mapaRetornoSalaLateral;
    private String pontoSaidaRetornoSalaLateral;
    private boolean armazemRetornoSalaLateral;
    private Point2D spawnRetornoSalaLateral;
    private int inventarioShurikens;
    private int inventarioBombasFumaca;
    private int inventarioKitsMedicos;
    private boolean inventarioKatana;
    private final Set<String> bausAbertosPersistentes = new HashSet<>();

    /** Item que o subchefe deixa no chão após uma investida. */
    private static final class ItemColetavel extends Circle {
        private final SubchefeSamurai.TipoItem tipo;

        private ItemColetavel(
                Point2D posicao,
                SubchefeSamurai.TipoItem tipo
        ) {
            super(posicao.getX(), posicao.getY(), 11);
            this.tipo = tipo;
            setFill(switch (tipo) {
                case SHURIKEN -> Color.SILVER;
                case BOMBA_FUMACA -> Color.web("#778899");
                case KATANA -> Color.web("#f4c542");
            });
            setStroke(switch (tipo) {
                case SHURIKEN -> Color.web("#263238");
                case BOMBA_FUMACA -> Color.web("#d8e0e7");
                case KATANA -> Color.web("#fff0a6");
            });
            setStrokeWidth(3);
            setManaged(false);
            setMouseTransparent(true);
        }

        private boolean tocou(Rectangle jogador) {
            return getBoundsInParent().intersects(
                    jogador.getBoundsInParent()
            );
        }
    }

    private record PortaLateral(
            Rectangle2D area,
            String mapaDestino,
            Point2D pontoRetorno
    ) {}

    /**
     * Método executado quando o JavaFX é iniciado.
     *
     * @param janela janela principal do jogo.
     */
    @Override
    public void start(Stage janela) {
        iniciarFase(
                janela,
                "entrada_labirinto.tmx",
                "saida_escadaria",
                false,
                100
        );
    }

    /**
     * Monta uma fase completa usando o mapa e a saída informados.
     */
    private void iniciarFase(
            Stage janela,
            String nomeMapa,
            String nomePontoSaida,
            boolean armazem,
            int vidaInicial
    ) {
        iniciarFase(
                janela, nomeMapa, nomePontoSaida,
                armazem, vidaInicial, null
        );
    }

    private void iniciarFase(
            Stage janela,
            String nomeMapa,
            String nomePontoSaida,
            boolean armazem,
            int vidaInicial,
            Point2D spawnForcado
    ) {

        // Cria o objeto responsável por interpretar arquivos do Tiled.
        CarregadorMapa carregadorMapa = new CarregadorMapa();

        /*
         * Carrega todas as camadas de tiles do labirinto
         * e as desenha em um Canvas do JavaFX.
         */
        Canvas visualMapa =
                carregadorMapa.carregarVisualMapa(nomeMapa);

        /*
         * Também busca a posição da saída criada no Tiled.
         * Esse ponto será utilizado para detectar a chegada do jogador.
         */
        Point2D pontoSaida =
                carregadorMapa.obterPontoImportante(nomePontoSaida);

        /*
         * Painel que representa o mundo completo do jogo.
         *
         * O mapa e o jogador ficarão dentro dele. A câmera será simulada
         * movimentando este painel na direção contrária ao jogador.
         */
        Pane mundo = new Pane();

        // Faz o tamanho do mundo acompanhar o tamanho completo do mapa.
        mundo.setPrefSize(visualMapa.getWidth(), visualMapa.getHeight());

        /*
         * O mapa precisa ser adicionado antes do jogador.
         * Assim, o jogador será desenhado por cima dos tiles.
         */
        mundo.getChildren().add(visualMapa);

        /*
         * Cria um guarda provisório para cada rota desenhada no Tiled.
         * Eles são adicionados antes do jogador para ficarem atrás dele.
         */
        List<GuardaPatrulha> guardas = new ArrayList<>();

        carregadorMapa.obterRotasGuardas().forEach((nome, rota) -> {
            GuardaPatrulha guarda = new GuardaPatrulha(rota);
            guarda.setId(nome);
            guarda.atualizarCampoVisao(carregadorMapa);
            guardas.add(guarda);
            mundo.getChildren().add(guarda);
        });

        boolean arenaSubchefe = "arena_subchefe.tmx".equals(nomeMapa);
        SubchefeSamurai subchefe = arenaSubchefe
                ? new SubchefeSamurai(new Point2D(480, 285))
                : null;
        if (subchefe != null) {
            mundo.getChildren().add(subchefe);
        }

        // Disparos existentes no mundo durante o estado de alerta.
        List<Projetil> projeteis = new ArrayList<>();

        // Bombas e shurikens arremessadas pelo subchefe.
        List<ProjetilSubchefe> projeteisSubchefe = new ArrayList<>();

        // Shurikens que o jogador lançou e ainda estão voando.
        List<ShurikenJogador> shurikensLancadas = new ArrayList<>();

        // Baús desenhados como retângulos na camada ItensInterativos.
        List<CarregadorMapa.ObjetoInterativo> baus =
                carregadorMapa.obterItensInterativos().stream()
                        .filter(item -> item.nome().toLowerCase()
                                .startsWith("bau_"))
                        .toList();
        Set<String> bausAbertos = new HashSet<>();
        Map<String, ImageView> visuaisBaus = new HashMap<>();

        for (CarregadorMapa.ObjetoInterativo bau : baus) {
            String chavePersistente = nomeMapa + "#" + bau.nome();
            boolean usarSpriteBau = armazem
                    || "patio_noturno.tmx".equals(nomeMapa);
            ImageView visualBau = usarSpriteBau
                    ? criarVisualBau(bau)
                    : null;

            if (bausAbertosPersistentes.contains(chavePersistente)) {
                bausAbertos.add(bau.nome());
                marcarBauAberto(visualBau);
            } else if (visualBau != null) {
                visuaisBaus.put(bau.nome(), visualBau);
            }

            if (visualBau != null) {
                mundo.getChildren().add(visualBau);
            }
        }

        // Itens soltos pelo subchefe e coletados ao encostar neles.
        List<ItemColetavel> itensDerrubados = new ArrayList<>();

        // Arrays permitem alterar esses valores dentro do AnimationTimer.
        boolean[] alertaAtivo = {false};
        boolean[] jogadorDerrotado = {false};
        boolean[] faseConcluida = {false};
        int[] vidaJogador = {vidaInicial};
        int[] shurikens = {inventarioShurikens};
        int[] bombasFumaca = {inventarioBombasFumaca};
        int[] kitsMedicos = {inventarioKitsMedicos};
        boolean[] teclaEConsumida = {false};
        boolean[] teclaHConsumida = {false};
        boolean[] teclaQConsumida = {false};
        boolean[] teclaEspacoConsumida = {false};
        boolean[] teclaFConsumida = {false};
        Point2D[] ultimaDirecaoJogador = {new Point2D(0, -1)};
        Circle[] nuvemFumaca = {null};
        double[] tempoFumaca = {0};
        double[] tempoMensagemColeta = {0};
        double[] tempoSemVerJogador = {0};
        Point2D[] ultimaPosicaoVista = {new Point2D(0, 0)};

        /*
         * Cria um jogador provisório.
         *
         * Ele possui 32 pixels de largura e 32 de altura,
         * o mesmo tamanho dos blocos utilizados no Tiled.
         */
        Rectangle jogador = new Rectangle(32, 32);

        // Define a cor provisória do jogador.
        jogador.setFill(Color.DODGERBLUE);

        // Busca no arquivo TMX o ponto criado com esse nome no Tiled.
        Point2D pontoSpawn = spawnForcado != null
                ? spawnForcado
                : carregadorMapa.obterPontoImportante("spawn_jogador");

        /*
         * No Tiled, o ponto marca o centro inferior do personagem.
         * Por isso descontamos metade da largura e toda a altura.
         */
        double posicaoInicialX =
                pontoSpawn.getX() - jogador.getWidth() / 2.0;
        double posicaoInicialY =
                pontoSpawn.getY() - jogador.getHeight();

        /*
         * Também limitamos a posição ao tamanho do mapa, pois o marcador
         * pode estar exatamente sobre a borda inferior do cenário.
         */
        jogador.setX(Math.max(
                0,
                Math.min(
                        posicaoInicialX,
                        visualMapa.getWidth() - jogador.getWidth()
                )
        ));

        jogador.setY(Math.max(
                0,
                Math.min(
                        posicaoInicialY,
                        visualMapa.getHeight() - jogador.getHeight()
                )
        ));

        // Adiciona o jogador ao painel principal.
        mundo.getChildren().add(jogador);

        /*
         * Este painel representa somente a parte do mundo que aparece
         * dentro da janela. Ele funciona como a área visível da câmera.
         */
        Pane areaVisivel = new Pane();
        areaVisivel.getChildren().add(mundo);

        /*
         * Aviso exibido quando o jogador alcança a saída do labirinto.
         * Como ele pertence à área visível, e não ao mundo, permanece
         * parado na tela mesmo enquanto a câmera se movimenta.
         */
        String textoSaidaPrincipal =
                "arena_subchefe.tmx".equals(nomeMapa)
                        ? "Saída trancada — derrote o subchefe"
                        : "patio_noturno.tmx".equals(nomeMapa)
                        ? "Entrada da arena — pressione E para continuar"
                        : nomeMapa.startsWith("sala_lateral_")
                        ? "Voltar pela porta — pressione E"
                        : "sala_treinamento.tmx".equals(nomeMapa)
                        ? "Entrada do pátio — pressione E para continuar"
                        : "dojo_guardas.tmx".equals(nomeMapa)
                        ? "Entrada da sala de treino — pressione E para continuar"
                        : armazem
                        ? "Saída encontrada — pressione E para continuar"
                        : "Escadaria encontrada — pressione E para entrar";
        Label avisoSaida = new Label(textoSaidaPrincipal);

        List<PortaLateral> portasLaterais =
                obterPortasLaterais(nomeMapa);

        // Define uma aparência provisória de interface para o aviso.
        avisoSaida.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-padding: 12px 18px;"
                        + "-fx-background-radius: 8px;"
        );

        // Posiciona a mensagem no canto superior esquerdo da janela.
        avisoSaida.setLayoutX(20);
        avisoSaida.setLayoutY(20);

        // O aviso começa escondido e não interfere com o mouse.
        avisoSaida.setVisible(false);
        avisoSaida.setMouseTransparent(true);

        // Adiciona depois do mundo para desenhá-lo por cima do mapa.
        areaVisivel.getChildren().add(avisoSaida);

        Label avisoAlerta = new Label("ALERTA: os guardas viram você!");
        avisoAlerta.setStyle(
                "-fx-background-color: rgba(120, 0, 0, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoAlerta.setLayoutX(20);
        avisoAlerta.setLayoutY(76);
        avisoAlerta.setVisible(false);
        avisoAlerta.setMouseTransparent(true);

        Label indicadorSubchefe = new Label();
        indicadorSubchefe.setStyle(
                "-fx-background-color: rgba(70, 10, 10, 0.92);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorSubchefe.setLayoutX(350);
        indicadorSubchefe.setLayoutY(20);
        indicadorSubchefe.setText(arenaSubchefe
                ? "Subchefe — Vida: "
                        + subchefe.obterVida() + "/"
                        + subchefe.obterVidaMaxima() + "  |  "
                        + textoControleKatana()
                : "");
        indicadorSubchefe.setVisible(arenaSubchefe);
        indicadorSubchefe.setMouseTransparent(true);

        Label indicadorVida =
                new Label("Vida: " + vidaJogador[0]);
        indicadorVida.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorVida.setLayoutX(LARGURA_JANELA - 130);
        indicadorVida.setLayoutY(20);
        indicadorVida.setMouseTransparent(true);


        Label avisoBau = new Label("Pressione E para abrir o baú");
        avisoBau.setStyle(
                "-fx-background-color: rgba(92, 61, 16, 0.94);"
                        + "-fx-text-fill: #ffe4a3;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoBau.setLayoutX(20);
        avisoBau.setLayoutY(132);
        avisoBau.setVisible(false);
        avisoBau.setMouseTransparent(true);

        Label indicadorInventario =
                new Label(textoInventario(
                        shurikens[0], bombasFumaca[0], kitsMedicos[0]
                ));
        indicadorInventario.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 15px;"
                        + "-fx-padding: 9px 13px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorInventario.setLayoutX(LARGURA_JANELA - 350);
        indicadorInventario.setLayoutY(76);
        // Depois que o jogador chega ao armazém (sala 2), o inventário
        // acompanha todas as fases e salas laterais seguintes.
        boolean mostrarInventario =
                !"entrada_labirinto.tmx".equals(nomeMapa);
        indicadorInventario.setVisible(mostrarInventario);
        indicadorInventario.setMouseTransparent(true);


        Label controlesItens =
                new Label(
                        arenaSubchefe
                                ? inventarioKatana
                                ? "F: katana | Espaço: shuriken | Q: fumaça | H: kit"
                                : "Katana: 2ª investida | Espaço: shuriken | Q: fumaça"
                                : "Espaço: shuriken  |  H: kit  |  Q: fumaça"
                );
        controlesItens.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.82);"
                        + "-fx-text-fill: #d8dde6;"
                        + "-fx-font-size: 13px;"
                        + "-fx-padding: 7px 11px;"
                        + "-fx-background-radius: 7px;"
        );
        controlesItens.setLayoutX(LARGURA_JANELA - 350);
        controlesItens.setLayoutY(121);
        controlesItens.setVisible(mostrarInventario);
        controlesItens.setMouseTransparent(true);

        Label avisoColeta = new Label();
        avisoColeta.setStyle(
                "-fx-background-color: rgba(20, 82, 48, 0.94);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoColeta.setLayoutX(20);
        avisoColeta.setLayoutY(188);
        avisoColeta.setVisible(false);
        avisoColeta.setMouseTransparent(true);

        /*
         * Painel central usado tanto ao concluir o labirinto quanto ao perder.
         * Ele fica fora do mundo para não se mover junto com a câmera.
         */
        Label painelResultado = new Label();
        painelResultado.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.95);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 22px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-alignment: center;"
                        + "-fx-text-alignment: center;"
                        + "-fx-padding: 24px;"
                        + "-fx-background-radius: 12px;"
                        + "-fx-border-color: rgba(255, 255, 255, 0.25);"
                        + "-fx-border-radius: 12px;"
        );
        painelResultado.setLayoutX((LARGURA_JANELA - 460) / 2.0);
        painelResultado.setLayoutY((ALTURA_JANELA - 190) / 2.0);
        painelResultado.setPrefSize(460, 190);
        painelResultado.setVisible(false);
        painelResultado.setMouseTransparent(true);

        areaVisivel.getChildren().addAll(
                avisoAlerta,
                indicadorSubchefe,
                indicadorVida,
                indicadorInventario,
                controlesItens,
                avisoBau,
                avisoColeta,
                painelResultado
        );

        // Cria uma janela menor que o mapa para permitir o uso da câmera.
        Scene cena = new Scene(areaVisivel, LARGURA_JANELA, ALTURA_JANELA);

        // Define uma cor escura temporária para o fundo.
        areaVisivel.setStyle("-fx-background-color: #20252b;");

        /*
         * Quando uma tecla for pressionada,
         * ela será adicionada ao conjunto.
         */
        cena.setOnKeyPressed(evento ->
                teclasPressionadas.add(evento.getCode())
        );

        /*
         * Quando a tecla for solta,
         * ela será removida do conjunto.
         */
        cena.setOnKeyReleased(evento ->
                teclasPressionadas.remove(evento.getCode())
        );

        /*
         * Controla a atualização contínua do jogo.
         *
         * O método handle() é chamado várias vezes por segundo,
         * permitindo uma movimentação suave.
         */
        AnimationTimer loopDoJogo = new AnimationTimer() {

            // Armazena o instante em que o quadro anterior foi executado.
            private long tempoAnterior = 0;

            // Evita recalcular os caminhos em todos os quadros.
            private double tempoParaRecalcularPerseguicao = 0;

            @Override
            public void handle(long tempoAtual) {

                /*
                 * Na primeira execução ainda não existe um quadro anterior.
                 * Por isso apenas guardamos o tempo atual.
                 */
                if (tempoAnterior == 0) {
                    tempoAnterior = tempoAtual;
                    return;
                }

                /*
                 * Calcula quantos segundos passaram desde o quadro anterior.
                 *
                 * O JavaFX fornece o tempo em nanossegundos,
                 * então dividimos por 1 bilhão.
                 */
                double tempoDecorrido =
                        (tempoAtual - tempoAnterior) / 1_000_000_000.0;

                // Evita saltos grandes quando a janela fica momentaneamente parada.
                tempoDecorrido = Math.min(tempoDecorrido, 0.05);

                // Atualiza o tempo usado no próximo quadro.
                tempoAnterior = tempoAtual;

                /*
                 * Depois da vitória ou derrota, R recria a fase inteira.
                 * O loop antigo é encerrado antes de montar a nova cena.
                 */
                if ((faseConcluida[0] || jogadorDerrotado[0])
                        && teclasPressionadas.contains(KeyCode.R)) {
                    stop();
                    teclasPressionadas.clear();
                    iniciarFase(
                            janela,
                            nomeMapa,
                            nomePontoSaida,
                            armazem,
                            100
                    );
                    return;
                }

                // Controla o tempo da mensagem exibida depois de abrir um baú.
                if (tempoMensagemColeta[0] > 0) {
                    tempoMensagemColeta[0] -= tempoDecorrido;
                    avisoColeta.setVisible(true);
                } else {
                    avisoColeta.setVisible(false);
                }

                boolean teclaEPressionada =
                        teclasPressionadas.contains(KeyCode.E);

                if (!teclaEPressionada) {
                    teclaEConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.H)) {
                    teclaHConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.Q)) {
                    teclaQConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.SPACE)) {
                    teclaEspacoConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.F)) {
                    teclaFConsumida[0] = false;
                }

                // Congela guardas, disparos e jogador enquanto o resultado aparece.
                if (faseConcluida[0] || jogadorDerrotado[0]) {
                    return;
                }


                // A nuvem permanece no lugar em que a bomba foi lançada.
                if (nuvemFumaca[0] != null) {
                    tempoFumaca[0] -= tempoDecorrido;

                    if (tempoFumaca[0] <= 0) {
                        mundo.getChildren().remove(nuvemFumaca[0]);
                        nuvemFumaca[0] = null;
                    }
                }

                if (teclasPressionadas.contains(KeyCode.H)
                        && !teclaHConsumida[0]
                        && kitsMedicos[0] > 0
                        && vidaJogador[0] < 100) {
                    teclaHConsumida[0] = true;
                    kitsMedicos[0]--;
                    vidaJogador[0] = Math.min(100, vidaJogador[0] + 30);
                    indicadorVida.setText("Vida: " + vidaJogador[0]);
                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.setText(textoInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    ));
                    avisoColeta.setText("Kit médico usado: +30 de vida");
                    tempoMensagemColeta[0] = 2.5;
                }

                if (teclasPressionadas.contains(KeyCode.Q)
                        && !teclaQConsumida[0]
                        && bombasFumaca[0] > 0
                        && nuvemFumaca[0] == null) {
                    teclaQConsumida[0] = true;
                    bombasFumaca[0]--;

                    Point2D centroFumaca = centroDoJogador(jogador);
                    Circle novaNuvem = new Circle(
                            centroFumaca.getX(),
                            centroFumaca.getY(),
                            72,
                            Color.rgb(125, 132, 140, 0.58)
                    );
                    novaNuvem.setStroke(Color.rgb(205, 210, 215, 0.68));
                    novaNuvem.setStrokeWidth(3);
                    novaNuvem.setMouseTransparent(true);
                    nuvemFumaca[0] = novaNuvem;
                    tempoFumaca[0] = 5.0;
                    mundo.getChildren().add(novaNuvem);

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.setText(textoInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    ));
                    avisoColeta.setText(
                            "Bomba de fumaça usada: visão bloqueada por 5s"
                    );
                    tempoMensagemColeta[0] = 2.5;
                }


                if (teclasPressionadas.contains(KeyCode.SPACE)
                        && !teclaEspacoConsumida[0]
                        && shurikens[0] > 0) {
                    teclaEspacoConsumida[0] = true;
                    shurikens[0]--;

                    ShurikenJogador shuriken = new ShurikenJogador(
                            centroDoJogador(jogador),
                            ultimaDirecaoJogador[0]
                    );
                    shurikensLancadas.add(shuriken);
                    mundo.getChildren().add(shuriken);

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.setText(textoInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    ));
                }

                // Na arena, F só usa a katana depois que ela for coletada.
                if (arenaSubchefe
                        && subchefe != null
                        && !subchefe.estaDerrotado()
                        && teclasPressionadas.contains(KeyCode.F)
                        && !teclaFConsumida[0]) {
                    teclaFConsumida[0] = true;

                    if (!inventarioKatana) {
                        avisoColeta.setText(
                                "A katana cairá na segunda investida!"
                        );
                        tempoMensagemColeta[0] = 1.5;
                    } else if (subchefe.receberGolpe(jogador)) {
                        indicadorSubchefe.setText(
                                "Subchefe — Vida: "
                                        + subchefe.obterVida() + "/"
                                        + subchefe.obterVidaMaxima()
                                        + "  |  " + textoControleKatana()
                        );
                        avisoColeta.setText("Golpe acertado!");
                        tempoMensagemColeta[0] = 0.7;

                        if (subchefe.estaDerrotado()) {
                            mundo.getChildren().remove(subchefe);
                            mundo.getChildren().removeAll(
                                    projeteisSubchefe
                            );
                            projeteisSubchefe.clear();
                            indicadorSubchefe.setText(
                                    "Subchefe derrotado — saída liberada"
                            );
                            avisoColeta.setText(
                                    "Subchefe derrotado! A porta superior abriu."
                            );
                            tempoMensagemColeta[0] = 3.0;
                        }
                    }
                }

                boolean jogadorProtegidoPelaFumaca =
                        nuvemFumaca[0] != null
                                && centroDoJogador(jogador).distance(
                                        nuvemFumaca[0].getCenterX(),
                                        nuvemFumaca[0].getCenterY()
                                ) <= nuvemFumaca[0].getRadius();

                if (arenaSubchefe
                        && subchefe != null
                        && !subchefe.estaDerrotado()) {
                    int danoSubchefe = subchefe.atualizar(
                            tempoDecorrido,
                            jogador,
                            carregadorMapa,
                            jogadorProtegidoPelaFumaca
                    );

                    if (subchefe.consumirChamadoReforcos()) {
                        List<GuardaPatrulha> reforcos = List.of(
                                new GuardaPatrulha(List.of(
                                        new Point2D(112, 134),
                                        new Point2D(112, 455)
                                )),
                                new GuardaPatrulha(List.of(
                                        new Point2D(848, 134),
                                        new Point2D(848, 455)
                                ))
                        );

                        for (int indice = 0;
                             indice < reforcos.size();
                             indice++) {
                            GuardaPatrulha guarda = reforcos.get(indice);
                            guarda.setId("reforco_subchefe_" + (indice + 1));
                            guarda.ativarAlerta();
                            guarda.definirCaminhoPerseguicao(
                                    carregadorMapa.encontrarCaminho(
                                            guarda.obterPosicao(),
                                            centroDoJogador(jogador)
                                    )
                            );
                            guarda.atualizarCampoVisao(carregadorMapa);
                            guardas.add(guarda);
                            mundo.getChildren().add(guarda);
                        }

                        alertaAtivo[0] = true;
                        tempoSemVerJogador[0] = 0;
                        tempoParaRecalcularPerseguicao = 0;
                        ultimaPosicaoVista[0] = centroDoJogador(jogador);
                        avisoAlerta.setText(
                                "O subchefe chamou dois guardas!"
                        );
                        avisoAlerta.setVisible(true);
                        avisoColeta.setText(
                                "REFORÇOS! Dois guardas entraram na arena."
                        );
                        tempoMensagemColeta[0] = 2.8;
                    }

                    SubchefeSamurai.TipoItem tipoDerrubado =
                            subchefe.consumirItemDerrubado();
                    SubchefeSamurai.TipoAtaqueDistancia ataqueDistancia =
                            subchefe.consumirAtaqueDistancia();

                    if (ataqueDistancia != null) {
                        ProjetilSubchefe projetil = new ProjetilSubchefe(
                                subchefe.obterPosicao(),
                                centroDoJogador(jogador),
                                ataqueDistancia
                        );
                        projeteisSubchefe.add(projetil);
                        mundo.getChildren().add(projetil);
                        avisoColeta.setText(
                                ataqueDistancia
                                        == SubchefeSamurai
                                        .TipoAtaqueDistancia.BOMBA
                                        ? "O subchefe lançou uma bomba!"
                                        : "O subchefe lançou uma shuriken!"
                        );
                        tempoMensagemColeta[0] = 1.0;
                    }

                    if (tipoDerrubado != null) {
                        ItemColetavel item = new ItemColetavel(
                                subchefe.obterPosicao(),
                                tipoDerrubado
                        );
                        itensDerrubados.add(item);
                        mundo.getChildren().add(item);
                        avisoColeta.setText(switch (tipoDerrubado) {
                            case SHURIKEN ->
                                    "A investida derrubou uma shuriken!";
                            case BOMBA_FUMACA ->
                                    "A investida derrubou uma bomba de fumaça!";
                            case KATANA ->
                                    "A segunda investida derrubou a katana!";
                        });
                        tempoMensagemColeta[0] = 1.8;
                    }

                    if (danoSubchefe > 0
                            && !jogadorProtegidoPelaFumaca
                            && !jogadorDerrotado[0]) {
                        vidaJogador[0] = Math.max(
                                0, vidaJogador[0] - danoSubchefe
                        );
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            jogadorDerrotado[0] = true;
                            avisoAlerta.setVisible(false);
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "O subchefe derrotou você\n\n"
                                            + "Pressione R para tentar novamente"
                            );
                            painelResultado.setVisible(true);
                            teclasPressionadas.clear();
                        }
                    }
                }

                Iterator<ProjetilSubchefe> iteradorProjeteisSubchefe =
                        projeteisSubchefe.iterator();

                while (iteradorProjeteisSubchefe.hasNext()) {
                    ProjetilSubchefe projetil =
                            iteradorProjeteisSubchefe.next();
                    int dano = projetil.atualizar(
                            tempoDecorrido, jogador, carregadorMapa
                    );

                    if (dano > 0
                            && !jogadorProtegidoPelaFumaca
                            && !jogadorDerrotado[0]) {
                        vidaJogador[0] = Math.max(
                                0, vidaJogador[0] - dano
                        );
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            jogadorDerrotado[0] = true;
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "O subchefe derrotou você\n\n"
                                            + "Pressione R para tentar novamente"
                            );
                            painelResultado.setVisible(true);
                            teclasPressionadas.clear();
                        }
                    }

                    if (projetil.deveSerRemovido()) {
                        mundo.getChildren().remove(projetil);
                        iteradorProjeteisSubchefe.remove();
                    }
                }

                Iterator<ItemColetavel> iteradorItens =
                        itensDerrubados.iterator();

                while (iteradorItens.hasNext()) {
                    ItemColetavel item = iteradorItens.next();

                    if (!item.tocou(jogador)) {
                        continue;
                    }

                    if (item.tipo == SubchefeSamurai.TipoItem.SHURIKEN) {
                        shurikens[0]++;
                        avisoColeta.setText("Shuriken coletada!");
                    } else if (item.tipo
                            == SubchefeSamurai.TipoItem.BOMBA_FUMACA) {
                        bombasFumaca[0]++;
                        avisoColeta.setText("Bomba de fumaça coletada!");
                    } else {
                        inventarioKatana = true;
                        avisoColeta.setText(
                                "Katana coletada! Pressione F para atacar."
                        );
                        controlesItens.setText(
                                "F: katana | Espaço: shuriken | Q: fumaça | H: kit"
                        );
                        indicadorSubchefe.setText(
                                "Subchefe — Vida: "
                                        + subchefe.obterVida() + "/"
                                        + subchefe.obterVidaMaxima()
                                        + "  |  " + textoControleKatana()
                        );
                    }

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.setText(textoInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    ));
                    tempoMensagemColeta[0] = 1.5;
                    mundo.getChildren().remove(item);
                    iteradorItens.remove();
                }

                if (!alertaAtivo[0]) {
                    // Alguns podem ainda estar voltando para suas rotas.
                    for (GuardaPatrulha guarda : guardas) {
                        if (guarda.estaRetornandoPatrulha()) {
                            guarda.atualizarRetornoPatrulha(tempoDecorrido);
                        } else {
                            guarda.atualizarPatrulha(tempoDecorrido);
                        }
                    }

                    boolean jogadorFoiVisto =
                            !jogadorProtegidoPelaFumaca
                                    && guardas.stream().anyMatch(
                            guarda -> guarda.consegueVer(
                                    jogador,
                                    carregadorMapa
                            )
                    );

                    if (jogadorFoiVisto) {
                        alertaAtivo[0] = true;
                        tempoSemVerJogador[0] = 0;
                        tempoParaRecalcularPerseguicao = 0;
                        ultimaPosicaoVista[0] = centroDoJogador(jogador);
                        avisoAlerta.setText(
                                "ALERTA: os guardas viram você!"
                        );
                        avisoAlerta.setVisible(true);

                        for (GuardaPatrulha guarda : guardas) {
                            guarda.ativarAlerta();
                        }
                    }
                } else {
                    boolean jogadorVisivelAgora =
                            !jogadorProtegidoPelaFumaca
                                    && guardas.stream().anyMatch(
                            guarda -> guarda.consegueVer(
                                    jogador,
                                    carregadorMapa
                            )
                    );

                    if (jogadorVisivelAgora) {
                        tempoSemVerJogador[0] = 0;
                        ultimaPosicaoVista[0] = centroDoJogador(jogador);
                        avisoAlerta.setText(
                                "ALERTA: os guardas viram você!"
                        );
                    } else {
                        tempoSemVerJogador[0] += tempoDecorrido;
                        avisoAlerta.setText(
                                "Os guardas estão procurando você..."
                        );
                    }

                    if (tempoSemVerJogador[0]
                            >= TEMPO_PARA_PERDER_JOGADOR) {
                        alertaAtivo[0] = false;
                        avisoAlerta.setVisible(false);

                        for (GuardaPatrulha guarda : guardas) {
                            Point2D pontoDeRetorno =
                                    guarda.obterPontoPatrulhaMaisProximo();
                            guarda.iniciarRetornoPatrulha(
                                    carregadorMapa.encontrarCaminho(
                                            guarda.obterPosicao(),
                                            pontoDeRetorno
                                    ),
                                    pontoDeRetorno
                            );
                        }
                    } else {
                        tempoParaRecalcularPerseguicao -= tempoDecorrido;

                        if (tempoParaRecalcularPerseguicao <= 0) {
                            List<Point2D> posicoesAoRedor =
                                    carregadorMapa.encontrarPosicoesAoRedor(
                                            ultimaPosicaoVista[0],
                                            guardas.size()
                                    );

                            for (int indice = 0;
                                 indice < guardas.size();
                                 indice++) {
                                GuardaPatrulha guarda = guardas.get(indice);
                                Point2D destino = posicoesAoRedor.get(
                                        indice % posicoesAoRedor.size()
                                );
                                guarda.definirCaminhoPerseguicao(
                                        carregadorMapa.encontrarCaminho(
                                                guarda.obterPosicao(),
                                                destino
                                        )
                                );
                            }

                            tempoParaRecalcularPerseguicao = 0.35;
                        }

                        for (GuardaPatrulha guarda : guardas) {
                            guarda.atualizarPerseguicao(
                                    tempoDecorrido,
                                    jogadorVisivelAgora
                            );

                            if (!jogadorDerrotado[0]
                                    && !jogadorProtegidoPelaFumaca
                                    && guarda.podeDisparar(
                                            jogador,
                                            carregadorMapa,
                                            tempoDecorrido
                                    )) {
                                Projetil projetil = new Projetil(
                                        guarda.obterPosicao(),
                                        centroDoJogador(jogador)
                                );
                                projeteis.add(projetil);
                                mundo.getChildren().add(projetil);
                            }
                        }
                    }
                }

                /*
                 * Atualiza o desenho somente depois que todos os guardas se
                 * moveram ou viraram, evitando um cone visual atrasado.
                 */
                for (GuardaPatrulha guarda : guardas) {
                    guarda.atualizarCampoVisao(carregadorMapa);
                }

                // Atualiza disparos, colisões com paredes e dano no jogador.
                Iterator<Projetil> iteradorProjeteis = projeteis.iterator();

                while (iteradorProjeteis.hasNext()) {
                    Projetil projetil = iteradorProjeteis.next();
                    projetil.atualizar(tempoDecorrido);
                    boolean atingiuJogador = !jogadorDerrotado[0]
                            && projetil.atingiu(jogador);

                    if (atingiuJogador) {
                        vidaJogador[0] = Math.max(0, vidaJogador[0] - 10);
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            jogadorDerrotado[0] = true;
                            avisoAlerta.setVisible(false);
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "Você foi derrotado\n\n"
                                            + "Pressione R para tentar novamente"
                            );
                            painelResultado.setVisible(true);
                            teclasPressionadas.clear();
                        }
                    }

                    if (atingiuJogador
                            || projetil.deveSerRemovido(carregadorMapa)) {
                        mundo.getChildren().remove(projetil);
                        iteradorProjeteis.remove();
                    }
                }


                // Atualiza as shurikens e remove o guarda atingido.
                Iterator<ShurikenJogador> iteradorShurikens =
                        shurikensLancadas.iterator();

                while (iteradorShurikens.hasNext()) {
                    ShurikenJogador shuriken = iteradorShurikens.next();
                    shuriken.atualizar(tempoDecorrido);
                    boolean atingiuGuarda = false;

                    Iterator<GuardaPatrulha> iteradorGuardas =
                            guardas.iterator();

                    while (iteradorGuardas.hasNext()) {
                        GuardaPatrulha guarda = iteradorGuardas.next();

                        if (shuriken.atingiu(guarda)) {
                            boolean guardaDerrotado = guarda.receberDano(1);
                            atingiuGuarda = true;

                            if (guardaDerrotado) {
                                mundo.getChildren().remove(guarda);
                                iteradorGuardas.remove();
                                avisoColeta.setText("Guarda nocauteado!");
                                tempoMensagemColeta[0] = 2.0;
                            } else {
                                avisoColeta.setText(
                                        "Guarda atingido — Vida: "
                                                + guarda.obterVida() + "/"
                                                + guarda.obterVidaMaxima()
                                );
                                tempoMensagemColeta[0] = 1.0;
                            }

                            break;
                        }
                    }

                    boolean atingiuSubchefe = false;

                    if (!atingiuGuarda
                            && subchefe != null
                            && !subchefe.estaDerrotado()
                            && shuriken.atingiu(subchefe)) {
                        subchefe.receberDano(1);
                        atingiuSubchefe = true;
                        indicadorSubchefe.setText(
                                "Subchefe — Vida: "
                                        + subchefe.obterVida() + "/"
                                        + subchefe.obterVidaMaxima()
                                        + "  |  " + textoControleKatana()
                        );
                        avisoColeta.setText("Shuriken acertou o subchefe!");
                        tempoMensagemColeta[0] = 0.9;

                        if (subchefe.estaDerrotado()) {
                            mundo.getChildren().remove(subchefe);
                            mundo.getChildren().removeAll(
                                    projeteisSubchefe
                            );
                            projeteisSubchefe.clear();
                            indicadorSubchefe.setText(
                                    "Subchefe derrotado — saída liberada"
                            );
                            avisoColeta.setText(
                                    "Subchefe derrotado! A porta superior abriu."
                            );
                            tempoMensagemColeta[0] = 3.0;
                        }
                    }

                    if (atingiuGuarda
                            || atingiuSubchefe
                            || shuriken.deveSerRemovida(carregadorMapa)) {
                        mundo.getChildren().remove(shuriken);
                        iteradorShurikens.remove();
                    }
                }

                // Direção horizontal do jogador.
                double direcaoX = 0;

                // Direção vertical do jogador.
                double direcaoY = 0;

                // W movimenta o jogador para cima.
                if (teclasPressionadas.contains(KeyCode.W)) {
                    direcaoY -= 1;
                }

                // S movimenta o jogador para baixo.
                if (teclasPressionadas.contains(KeyCode.S)) {
                    direcaoY += 1;
                }

                // A movimenta o jogador para a esquerda.
                if (teclasPressionadas.contains(KeyCode.A)) {
                    direcaoX -= 1;
                }

                // D movimenta o jogador para a direita.
                if (teclasPressionadas.contains(KeyCode.D)) {
                    direcaoX += 1;
                }

                // Depois de perder toda a vida, o jogador não se movimenta.
                if (jogadorDerrotado[0]) {
                    direcaoX = 0;
                    direcaoY = 0;
                }

                /*
                 * Impede que a movimentação diagonal seja mais rápida.
                 *
                 * Por exemplo, W + D movimenta simultaneamente
                 * para cima e para a direita.
                 */
                if (direcaoX != 0 && direcaoY != 0) {
                    double ajusteDiagonal = Math.sqrt(2);

                    direcaoX /= ajusteDiagonal;
                    direcaoY /= ajusteDiagonal;
                }

                if (direcaoX != 0 || direcaoY != 0) {
                    ultimaDirecaoJogador[0] =
                            new Point2D(direcaoX, direcaoY).normalize();
                }

                /*
                 * Calcula a nova posição usando:
                 *
                 * direção × velocidade × tempo decorrido.
                 */
                double novaPosicaoX = jogador.getX()
                        + direcaoX * VELOCIDADE_JOGADOR * tempoDecorrido;

                double novaPosicaoY = jogador.getY()
                        + direcaoY * VELOCIDADE_JOGADOR * tempoDecorrido;

                /*
                 * Limita a posição horizontal para impedir
                 * que o jogador saia da janela.
                 */
                novaPosicaoX = Math.max(
                        0,
                        Math.min(novaPosicaoX,
                                visualMapa.getWidth() - jogador.getWidth())
                );

                /*
                 * Limita a posição vertical para impedir
                 * que o jogador saia da janela.
                 */
                novaPosicaoY = Math.max(
                        0,
                        Math.min(novaPosicaoY,
                                visualMapa.getHeight() - jogador.getHeight())
                );

                /*
                 * Se uma atualização de mapa deixou o jogador parcialmente
                 * dentro de um objeto, libera o próximo passo para ele sair.
                 * Assim não fica travado depois de uma colisão nova.
                 */
                boolean jogadorPresoEmObjeto =
                        carregadorMapa.possuiColisao(
                                jogador.getX(),
                                jogador.getY(),
                                jogador.getWidth(),
                                jogador.getHeight()
                        );

                if (jogadorPresoEmObjeto) {
                    jogador.setX(novaPosicaoX);
                    jogador.setY(novaPosicaoY);
                } else {

                    /*
                     * Testa primeiro o movimento horizontal.
                     * Separar os dois eixos permite que o jogador deslize
                     * ao longo de uma parede em vez de ficar completamente preso.
                     */
                    if (!carregadorMapa.possuiColisao(
                            novaPosicaoX,
                            jogador.getY(),
                            jogador.getWidth(),
                            jogador.getHeight()
                    )) {
                        jogador.setX(novaPosicaoX);
                    }

                    // Depois testa o movimento vertical usando o novo X aprovado.
                    if (!carregadorMapa.possuiColisao(
                            jogador.getX(),
                            novaPosicaoY,
                            jogador.getWidth(),
                            jogador.getHeight()
                    )) {
                        jogador.setY(novaPosicaoY);
                    }
                }

                /*
                 * Reposiciona o mundo depois de atualizar o jogador.
                 * Isso produz o efeito de uma câmera acompanhando-o.
                 */
                atualizarCamera(mundo, jogador, cena, visualMapa);


                CarregadorMapa.ObjetoInterativo bauProximo =
                        encontrarBauProximo(jogador, baus, bausAbertos);
                avisoBau.setVisible(bauProximo != null);

                if (bauProximo != null
                        && teclaEPressionada
                        && !teclaEConsumida[0]) {
                    teclaEConsumida[0] = true;
                    bausAbertos.add(bauProximo.nome());
                    bausAbertosPersistentes.add(
                            nomeMapa + "#" + bauProximo.nome()
                    );
                    ImageView visualBau = visuaisBaus.remove(
                            bauProximo.nome()
                    );
                    marcarBauAberto(visualBau);

                    String mensagemColeta;
                    String nomeBau = bauProximo.nome().toLowerCase();

                    if (nomeBau.contains("preparo_shuriken")) {
                        shurikens[0] += 4;
                        mensagemColeta = "Baú de preparo: +4 shurikens";
                    } else if (nomeBau.contains("preparo_fumaca")) {
                        bombasFumaca[0] += 2;
                        mensagemColeta =
                                "Baú de preparo: +2 bombas de fumaça";
                    } else {
                        int sorteio =
                                ThreadLocalRandom.current().nextInt(100);

                        if (sorteio < 50) {
                            int quantidade =
                                    ThreadLocalRandom.current().nextInt(3, 8);
                            shurikens[0] += quantidade;
                            mensagemColeta =
                                    "Baú aberto: +" + quantidade
                                            + " shurikens";
                        } else if (sorteio < 80) {
                            int quantidade =
                                    ThreadLocalRandom.current().nextInt(1, 3);
                            bombasFumaca[0] += quantidade;
                            mensagemColeta =
                                    "Baú aberto: +" + quantidade
                                            + " bomba(s) de fumaça";
                        } else {
                            kitsMedicos[0]++;
                            mensagemColeta =
                                    "Baú aberto: +1 kit médico";
                        }
                    }

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.setText(textoInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    ));
                    avisoColeta.setText(mensagemColeta);
                    tempoMensagemColeta[0] = 2.5;
                    avisoBau.setVisible(false);
                }

                /*
                 * Verifica continuamente se o jogador chegou perto
                 * do ponto de saída definido no Tiled.
                 */
                String textoSaidaAtual = arenaSubchefe
                        && subchefe != null
                        && subchefe.estaDerrotado()
                        ? "Saída liberada — pressione E para continuar"
                        : textoSaidaPrincipal;
                avisoSaida.setText(textoSaidaAtual);
                boolean jogadorNaSaida = atualizarAvisoSaida(
                        jogador,
                        pontoSaida,
                        avisoSaida
                );
                boolean subchefeBloqueiaSaida = arenaSubchefe
                        && subchefe != null
                        && !subchefe.estaDerrotado();

                if (subchefeBloqueiaSaida && jogadorNaSaida) {
                    avisoSaida.setText(
                            "Saída trancada — derrote o subchefe"
                    );
                    jogadorNaSaida = false;
                }
                PortaLateral portaProxima = encontrarPortaLateral(
                        jogador, portasLaterais
                );

                if (portaProxima != null) {
                    avisoSaida.setText(
                            "Entrar nesta sala — pressione E"
                    );
                    avisoSaida.setVisible(true);
                } else {
                    avisoSaida.setText(textoSaidaAtual);
                }

                if ((jogadorNaSaida || portaProxima != null)
                        && bauProximo == null
                        && teclaEPressionada
                        && !teclaEConsumida[0]) {
                    teclaEConsumida[0] = true;
                    avisoSaida.setVisible(false);
                    avisoAlerta.setVisible(false);
                    avisoBau.setVisible(false);

                    if (portaProxima != null) {
                        mapaRetornoSalaLateral = nomeMapa;
                        pontoSaidaRetornoSalaLateral = nomePontoSaida;
                        armazemRetornoSalaLateral = armazem;
                        spawnRetornoSalaLateral =
                                portaProxima.pontoRetorno();
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                portaProxima.mapaDestino(),
                                "saida_sala_lateral",
                                false,
                                vidaJogador[0]
                        );
                        return;
                    }

                    if (nomeMapa.startsWith("sala_lateral_")
                            && mapaRetornoSalaLateral != null) {
                        String mapaRetorno = mapaRetornoSalaLateral;
                        String pontoSaidaRetorno =
                                pontoSaidaRetornoSalaLateral;
                        boolean retornoArmazem =
                                armazemRetornoSalaLateral;
                        Point2D retornoSpawn = spawnRetornoSalaLateral;
                        mapaRetornoSalaLateral = null;
                        pontoSaidaRetornoSalaLateral = null;
                        spawnRetornoSalaLateral = null;
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                mapaRetorno,
                                pontoSaidaRetorno,
                                retornoArmazem,
                                vidaJogador[0],
                                retornoSpawn
                        );
                        return;
                    }

                    if ("entrada_labirinto.tmx".equals(nomeMapa)) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "armazem_dojo.tmx",
                                "saida_armazem",
                                true,
                                vidaJogador[0]
                        );
                        return;
                    }

                    if ("armazem_dojo.tmx".equals(nomeMapa)) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "dojo_guardas.tmx",
                                "saida_dojo",
                                false,
                                vidaJogador[0]
                        );
                        return;
                    }

                    if ("dojo_guardas.tmx".equals(nomeMapa)) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "sala_treinamento.tmx",
                                "saida_treinamento",
                                false,
                                vidaJogador[0]
                        );
                        return;
                    }

                    if ("sala_treinamento.tmx".equals(nomeMapa)) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "patio_noturno.tmx",
                                "saida_patio",
                                false,
                                vidaJogador[0]
                        );
                        return;
                    }

                    if ("patio_noturno.tmx".equals(nomeMapa)) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "arena_subchefe.tmx",
                                "saida_arena",
                                false,
                                vidaJogador[0]
                        );
                        return;
                    }

                    faseConcluida[0] = true;
                    painelResultado.setText(
                            "Subchefe derrotado!\n\n"
                                    + "A próxima área foi liberada.\n"
                                    + "Pressione R para explorar novamente"
                    );
                    painelResultado.setVisible(true);
                    teclasPressionadas.clear();
                }
            }
        };

        // Inicia o ciclo de atualização do jogo.
        loopDoJogo.start();

        // Configura e exibe a janela.
        janela.setTitle("Hangetsu: Shadow Dojo");
        janela.setScene(cena);

        // Mantém a janela no tamanho exato do mapa durante este primeiro teste.
        janela.setResizable(false);

        janela.show();

        // Posiciona a câmera corretamente antes do primeiro movimento.
        atualizarCamera(mundo, jogador, cena, visualMapa);

        // Confere também a saída antes do primeiro quadro do jogo.
        atualizarAvisoSaida(jogador, pontoSaida, avisoSaida);
    }

    /** Portas decorativas marcadas na sala de treinamento. */
    private List<PortaLateral> obterPortasLaterais(String nomeMapa) {
        if ("dojo_guardas.tmx".equals(nomeMapa)) {
            return List.of(
                    new PortaLateral(
                            new Rectangle2D(690, 82, 180, 124),
                            "sala_lateral_guarda.tmx",
                            new Point2D(780, 224)
                    )
            );
        }

        if (!"sala_treinamento.tmx".equals(nomeMapa)) {
            return List.of();
        }

        return List.of(
                new PortaLateral(
                        new Rectangle2D(42, 86, 92, 150),
                        "sala_lateral_deposito.tmx",
                        new Point2D(150, 165)
                ),
                new PortaLateral(
                        new Rectangle2D(42, 300, 92, 168),
                        "sala_lateral_vazia.tmx",
                        new Point2D(150, 385)
                ),
                new PortaLateral(
                        new Rectangle2D(826, 86, 92, 150),
                        "sala_lateral_guarda.tmx",
                        new Point2D(810, 165)
                ),
                new PortaLateral(
                        new Rectangle2D(826, 300, 92, 168),
                        "sala_lateral_deposito.tmx",
                        new Point2D(810, 385)
                )
        );
    }

    private PortaLateral encontrarPortaLateral(
            Rectangle jogador,
            List<PortaLateral> portas
    ) {
        Point2D centro = centroDoJogador(jogador);

        for (PortaLateral porta : portas) {
            if (porta.area().contains(centro)) {
                return porta;
            }
        }

        return null;
    }


    /**
     * Encontra o baú fechado mais próximo do jogador.
     */
    private CarregadorMapa.ObjetoInterativo encontrarBauProximo(
            Rectangle jogador,
            List<CarregadorMapa.ObjetoInterativo> baus,
            Set<String> bausAbertos
    ) {
        Point2D centroJogador = centroDoJogador(jogador);
        CarregadorMapa.ObjetoInterativo maisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        for (CarregadorMapa.ObjetoInterativo bau : baus) {
            if (bausAbertos.contains(bau.nome())) {
                continue;
            }

            double distancia = centroJogador.distance(bau.centro());

            if (distancia <= 56.0 && distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = bau;
            }
        }

        return maisProximo;
    }

    private ImageView criarVisualBau(
            CarregadorMapa.ObjetoInterativo bau
    ) {
        Image imagem = new Image(
                getClass().getResource(
                        "/tilesets/armazem/chests_32x32.png"
                ).toExternalForm()
        );
        ImageView visual = new ImageView(imagem);
        // Segunda coluna: baú de madeira fechado.
        visual.setViewport(new Rectangle2D(32, 0, 32, 32));
        // Centraliza o sprite e mantém sua base alinhada ao objeto do mapa.
        visual.setX(bau.x() + (bau.largura() - 32) / 2.0);
        visual.setY(bau.y() + bau.altura() - 32);
        visual.setFitWidth(32);
        visual.setFitHeight(32);
        visual.setSmooth(false);
        visual.setManaged(false);
        visual.setMouseTransparent(true);
        return visual;
    }

    private void marcarBauAberto(ImageView visualBau) {
        if (visualBau == null) {
            return;
        }

        // Primeira coluna: o mesmo baú com a tampa aberta.
        visualBau.setViewport(new Rectangle2D(0, 0, 32, 32));
    }

    private void salvarInventario(
            int shurikens,
            int bombasFumaca,
            int kitsMedicos
    ) {
        inventarioShurikens = shurikens;
        inventarioBombasFumaca = bombasFumaca;
        inventarioKitsMedicos = kitsMedicos;
    }

    private String textoInventario(
            int shurikens,
            int bombasFumaca,
            int kitsMedicos
    ) {
        return "Shurikens: " + shurikens
                + "  |  Fumaça: " + bombasFumaca
                + "  |  Kits: " + kitsMedicos;
    }

    private String textoControleKatana() {
        return inventarioKatana
                ? "F: katana"
                : "Katana: aguarde a 2ª investida";
    }

    private Point2D centroDoJogador(Rectangle jogador) {
        return new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );
    }

    /**
     * Exibe ou esconde o aviso da saída conforme a distância do jogador.
     *
     * @param jogador representação provisória do jogador.
     * @param pontoSaida posição da saída carregada do Tiled.
     * @param avisoSaida mensagem mostrada na interface.
     */
    private boolean atualizarAvisoSaida(
            Rectangle jogador,
            Point2D pontoSaida,
            Label avisoSaida
    ) {
        // Usa o centro do jogador para medir a distância até a saída.
        Point2D centroJogador = new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );

        /*
         * A saída é considerada alcançada dentro de um raio de 48 pixels,
         * equivalente a um bloco e meio do nosso mapa.
         */
        double distanciaAteSaida = centroJogador.distance(pontoSaida);

        // A porta shoji do dojo é larga. Um raio maior transforma toda a
        // abertura visível em entrada da sala de treino, em vez de exigir
        // que o jogador encontre um único ponto no centro da porta.
        double raioDaSaida = avisoSaida.getText().startsWith("Entrada")
                ? 100.0 : 48.0;
        boolean jogadorChegouNaSaida = distanciaAteSaida <= raioDaSaida;

        avisoSaida.setVisible(jogadorChegouNaSaida);
        return jogadorChegouNaSaida;
    }

    /**
     * Move o painel do mundo para manter o jogador visível.
     *
     * A câmera tenta centralizar o jogador, mas é limitada pelas bordas
     * do mapa para nunca mostrar uma região vazia fora do cenário.
     *
     * @param mundo painel que contém o mapa e os personagens.
     * @param jogador representação provisória do jogador.
     * @param cena área visível da janela.
     * @param visualMapa Canvas que informa o tamanho completo do mapa.
     */
    private void atualizarCamera(
            Pane mundo,
            Rectangle jogador,
            Scene cena,
            Canvas visualMapa
    ) {
        // Centro atual do jogador nas coordenadas do mapa.
        double centroJogadorX = jogador.getX() + jogador.getWidth() / 2.0;
        double centroJogadorY = jogador.getY() + jogador.getHeight() / 2.0;

        /*
         * Calcula quanto o mundo precisa ser deslocado para que o centro
         * do jogador coincida com o centro da área visível.
         */
        double deslocamentoX = cena.getWidth() / 2.0 - centroJogadorX;
        double deslocamentoY = cena.getHeight() / 2.0 - centroJogadorY;

        /*
         * Estes são os menores deslocamentos permitidos. Por exemplo,
         * quando a câmera chega ao lado direito, ela não pode continuar
         * movendo o mapa para a esquerda e revelar uma área vazia.
         */
        double limiteMinimoX = cena.getWidth() - visualMapa.getWidth();
        double limiteMinimoY = cena.getHeight() - visualMapa.getHeight();

        // Limita a câmera horizontalmente entre as duas bordas do mapa.
        deslocamentoX = Math.max(
                Math.min(0, limiteMinimoX),
                Math.min(deslocamentoX, 0)
        );

        // Limita a câmera verticalmente entre as duas bordas do mapa.
        deslocamentoY = Math.max(
                Math.min(0, limiteMinimoY),
                Math.min(deslocamentoY, 0)
        );

        // Aplica o deslocamento calculado a todo o mundo do jogo.
        mundo.setTranslateX(deslocamentoX);
        mundo.setTranslateY(deslocamentoY);
    }

    /**
     * Ponto de entrada do programa.
     *
     * @param args argumentos da execução.
     */
    public static void main(String[] args) {

        // Inicia o JavaFX e chama o método start().
        launch(args);
    }
}
