package com.dojogame;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.prefs.Preferences;

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
    private static final double VELOCIDADE_FURTIVA = 120.0;

    // Os passos só chamam a atenção quando o jogador está realmente perto.
    private static final double ALCANCE_AUDICAO_PASSOS = 145.0;
    private static final double ALCANCE_AUDICAO_FURTIVO = 42.0;
    private static final double ALCANCE_RUIDO_SHURIKEN = 180.0;
    private static final double ALCANCE_RUIDO_FUMACA = 230.0;
    private static final double INTERVALO_PASSOS = 0.34;
    private static final double INTERVALO_PASSOS_FURTIVOS = 0.48;

    // Tempo escondido necessário para os guardas abandonarem a perseguição.
    private static final double TEMPO_PARA_PERDER_JOGADOR = 8.0;

    private String mapaRetornoSalaLateral;
    private String pontoSaidaRetornoSalaLateral;
    private boolean armazemRetornoSalaLateral;
    private Point2D spawnRetornoSalaLateral;
    private int inventarioShurikens;
    private int inventarioBombasFumaca;
    private int inventarioKitsMedicos;
    private boolean inventarioKatana;
    private boolean abrirPortaAoCarregar;
    private final SomPassos somPassos = new SomPassos();
    private double volumeEfeitos = 0.75;
    private final Map<AcaoControle, KeyCode> atalhos =
            new EnumMap<>(AcaoControle.class);
    private final Set<String> bausAbertosPersistentes = new HashSet<>();
    private final List<ItensPerdidos> itensPerdidos = new ArrayList<>();
    private static final Preferences PREFERENCIAS =
            Preferences.userNodeForPackage(Main.class);

    private static final String CHAVE_MAPA = "progresso.mapa";
    private static final String CHAVE_SAIDA = "progresso.saida";
    private static final String CHAVE_ARMAZEM = "progresso.armazem";
    private static final String CHAVE_VIDA = "progresso.vida";
    private static final String CHAVE_SHURIKENS = "inventario.shurikens";
    private static final String CHAVE_FUMACA = "inventario.fumaca";
    private static final String CHAVE_KITS = "inventario.kits";
    private static final String CHAVE_KATANA = "inventario.katana";
    private static final String CHAVE_CHECKPOINT_SUBCHEFE =
            "progresso.checkpoint_subchefe";
    private static final String CHAVE_TUTORIAL = "tutorial.concluido";
    private static final String CHAVE_TUTORIAL_ITENS =
            "tutorial.itens.concluido";
    private static final String CHAVE_VOLUME_EFEITOS = "audio.efeitos";

    /** Menu da mochila, aberto somente quando o jogador quiser consultar itens. */
    private final class PainelInventario extends VBox {
        private final Label quantidadeShuriken = criarQuantidade();
        private final Label quantidadeFumaca = criarQuantidade();
        private final Label quantidadeKit = criarQuantidade();
        private final Label estadoKatana = criarQuantidade();
        private final Label atalhoShuriken = criarAtalho();
        private final Label atalhoFumaca = criarAtalho();
        private final Label atalhoKit = criarAtalho();
        private final Label atalhoKatana = criarAtalho();
        private final VBox slotKatana;
        private final Label rodapeControles = new Label();
        private final boolean arenaSubchefe;

        private PainelInventario(boolean arenaSubchefe) {
            this.arenaSubchefe = arenaSubchefe;

            Label titulo = new Label("MOCHILA");
            titulo.setStyle(
                    "-fx-text-fill: #ffe2a1;"
                            + "-fx-font-size: 17px;"
                            + "-fx-font-weight: bold;"
            );

            Label subtitulo = new Label("ITENS DE CAMPO");
            subtitulo.setStyle(
                    "-fx-text-fill: #b99758;"
                            + "-fx-font-size: 9px;"
                            + "-fx-font-weight: bold;"
            );

            GridPane itens = new GridPane();
            itens.setHgap(8);
            itens.setVgap(8);
            itens.add(criarSlot("SHURIKEN", "✦",
                    quantidadeShuriken, atalhoShuriken), 0, 0);
            itens.add(criarSlot("FUMAÇA", "●",
                    quantidadeFumaca, atalhoFumaca), 1, 0);
            itens.add(criarSlot("KIT MÉDICO", "+",
                    quantidadeKit, atalhoKit), 0, 1);

            slotKatana = criarSlot(
                    "KATANA", "/",
                    estadoKatana, atalhoKatana
            );
            itens.add(slotKatana, 1, 1);

            rodapeControles.setWrapText(true);
            rodapeControles.setPrefWidth(270);
            rodapeControles.setStyle(
                    "-fx-text-fill: #d8c69e;"
                            + "-fx-font-size: 11px;"
                            + "-fx-padding: 7px 2px 0 2px;"
            );

            setSpacing(6);
            setPadding(new Insets(13, 14, 13, 14));
            setAlignment(Pos.TOP_LEFT);
            setPrefWidth(310);
            setStyle(
                    "-fx-background-color: rgba(43, 25, 17, 0.97);"
                            + "-fx-background-radius: 3px;"
                            + "-fx-border-color: #2a160e, #b7853f, #5b351f;"
                            + "-fx-border-insets: 0, 3, 5;"
                            + "-fx-border-width: 3px, 2px, 1px;"
            );
            getChildren().addAll(titulo, subtitulo, itens, rodapeControles);
        }

        private VBox criarSlot(
                String nome,
                String icone,
                Label quantidade,
                Label atalho
        ) {
            Label cabecalho = new Label(icone + "  " + nome);
            cabecalho.setStyle(
                    "-fx-text-fill: #f0d49a;"
                            + "-fx-font-size: 11px;"
                            + "-fx-font-weight: bold;"
            );

            VBox slot = new VBox(1, cabecalho, quantidade, atalho);
            slot.setAlignment(Pos.CENTER_LEFT);
            slot.setPrefWidth(135);
            slot.setMinWidth(135);
            slot.setPadding(new Insets(7, 9, 7, 9));
            slot.setStyle(
                    "-fx-background-color: rgba(91, 57, 31, 0.82);"
                            + "-fx-border-color: #896036;"
                            + "-fx-border-width: 1px;"
            );
            return slot;
        }

        private Label criarQuantidade() {
            Label quantidade = new Label();
            quantidade.setStyle(
                    "-fx-text-fill: #fff1c9;"
                            + "-fx-font-size: 18px;"
                            + "-fx-font-weight: bold;"
            );
            return quantidade;
        }

        private Label criarAtalho() {
            Label atalho = new Label();
            atalho.setStyle(
                    "-fx-text-fill: #c3a878;"
                            + "-fx-font-size: 9px;"
            );
            return atalho;
        }

        private void atualizar(
                int shurikens,
                int bombasFumaca,
                int kitsMedicos,
                boolean possuiKatana
        ) {
            quantidadeShuriken.setText("x " + shurikens);
            quantidadeFumaca.setText("x " + bombasFumaca);
            quantidadeKit.setText("x " + kitsMedicos);
            estadoKatana.setText(possuiKatana ? "x 1" : "x 0");

            atalhoShuriken.setText("[" + nomeTecla(AcaoControle.SHURIKEN) + "]");
            atalhoFumaca.setText("[" + nomeTecla(AcaoControle.FUMACA) + "]");
            atalhoKit.setText("[" + nomeTecla(AcaoControle.KIT_MEDICO) + "]");
            atalhoKatana.setText("[" + nomeTecla(AcaoControle.KATANA) + "]");

            String rodape = "[" + nomeTecla(AcaoControle.FURTIVO)
                    + "] andar silenciosamente";
            if (arenaSubchefe && !possuiKatana) {
                rodape += "  •  Katana após a 2ª investida";
            }
            rodapeControles.setText(rodape);

            // A katana permanece no menu para o jogador saber que ainda
            // precisa encontrá-la, em vez de o espaço simplesmente sumir.
            slotKatana.setOpacity(possuiKatana ? 1.0 : 0.55);
        }
    }

    /** Botão com uma mochila desenhada em pixel art para combinar com o dojo. */
    private Button criarBotaoMochila() {
        Canvas desenho = new Canvas(42, 42);
        GraphicsContext pincel = desenho.getGraphicsContext2D();
        pincel.setImageSmoothing(false);

        // Contorno claro separa o ícone de cenários de madeira escura.
        pincel.setFill(Color.web("#f4cf77"));
        pincel.fillRect(9, 7, 24, 29);
        pincel.fillRect(6, 16, 3, 15);
        pincel.fillRect(33, 16, 3, 15);
        pincel.setFill(Color.web("#32180e"));
        pincel.fillRect(11, 9, 20, 25);
        pincel.setFill(Color.web("#bb6d2f"));
        pincel.fillRect(13, 7, 16, 7);
        pincel.fillRect(9, 16, 24, 16);
        pincel.setFill(Color.web("#e29a43"));
        pincel.fillRect(12, 18, 18, 10);
        pincel.setFill(Color.web("#613219"));
        pincel.fillRect(12, 16, 18, 5);
        pincel.fillRect(15, 26, 12, 5);
        pincel.setFill(Color.web("#ffe09a"));
        pincel.fillRect(18, 20, 6, 6);
        pincel.setFill(Color.web("#7f421f"));
        pincel.fillRect(6, 18, 3, 11);
        pincel.fillRect(33, 18, 3, 11);

        Label legenda = new Label("ITENS");
        legenda.setStyle(
                "-fx-text-fill: #ffe5aa;"
                        + "-fx-font-size: 9px;"
                        + "-fx-font-weight: bold;"
        );
        VBox conteudo = new VBox(-3, desenho, legenda);
        conteudo.setAlignment(Pos.CENTER);

        Button botao = new Button();
        botao.setGraphic(conteudo);
        botao.setPrefSize(66, 68);
        botao.setMinSize(66, 68);
        botao.setFocusTraversable(false);
        botao.setStyle(
                "-fx-background-color: rgba(74, 40, 21, 0.98);"
                        + "-fx-border-color: #1f0e08, #ffd477;"
                        + "-fx-border-insets: 0, 3;"
                        + "-fx-border-width: 3px, 2px;"
                        + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.85),"
                        + " 9, 0.25, 0, 3);"
                        + "-fx-padding: 3px;"
                        + "-fx-cursor: hand;"
        );
        return botao;
    }

    /** Item que o subchefe deixa no chão após uma investida. */
    private static final class ItemColetavel extends Pane {
        private final SubchefeSamurai.TipoItem tipo;
        private double tempoAntesDaColeta = 0.75;
        private double tempoAnimacao;

        private ItemColetavel(
                Point2D posicao,
                SubchefeSamurai.TipoItem tipo
        ) {
            this.tipo = tipo;
            setPrefSize(36, 36);
            setLayoutX(posicao.getX() - 18);
            setLayoutY(posicao.getY() - 18);

            Circle brilho = new Circle(18, 18, 16);
            brilho.setFill(Color.rgb(255, 211, 82, 0.20));
            brilho.setStroke(Color.rgb(255, 226, 130, 0.82));
            brilho.setStrokeWidth(2);

            if (tipo == SubchefeSamurai.TipoItem.SHURIKEN) {
                Polygon shuriken = new Polygon(
                        18.0, 4.0, 22.0, 14.0,
                        32.0, 10.0, 25.0, 18.0,
                        32.0, 26.0, 22.0, 22.0,
                        18.0, 32.0, 14.0, 22.0,
                        4.0, 26.0, 11.0, 18.0,
                        4.0, 10.0, 14.0, 14.0
                );
                shuriken.setFill(Color.web("#dce6eb"));
                shuriken.setStroke(Color.web("#263238"));
                shuriken.setStrokeWidth(2);
                getChildren().addAll(brilho, shuriken);
            } else if (tipo
                    == SubchefeSamurai.TipoItem.BOMBA_FUMACA) {
                Circle bomba = new Circle(18, 20, 10);
                bomba.setFill(Color.web("#56636b"));
                bomba.setStroke(Color.web("#d8e0e7"));
                bomba.setStrokeWidth(2);
                Line pavio = new Line(22, 11, 27, 6);
                pavio.setStroke(Color.web("#f4c542"));
                pavio.setStrokeWidth(3);
                getChildren().addAll(brilho, bomba, pavio);
            } else {
                Line lamina = new Line(8, 28, 27, 9);
                lamina.setStroke(Color.web("#f4f7f8"));
                lamina.setStrokeWidth(5);
                Line fio = new Line(9, 27, 28, 8);
                fio.setStroke(Color.web("#fff0a6"));
                fio.setStrokeWidth(2);
                Line guarda = new Line(7, 24, 13, 30);
                guarda.setStroke(Color.web("#d6a42f"));
                guarda.setStrokeWidth(4);
                Line cabo = new Line(7, 29, 4, 32);
                cabo.setStroke(Color.web("#6e3028"));
                cabo.setStrokeWidth(5);
                getChildren().addAll(brilho, lamina, fio, guarda, cabo);
            }

            setManaged(false);
            setMouseTransparent(true);
        }

        private void atualizar(double tempo) {
            tempoAntesDaColeta = Math.max(0, tempoAntesDaColeta - tempo);
            tempoAnimacao += tempo;
            setTranslateY(-3 + Math.sin(tempoAnimacao * 4.5) * 3);
        }

        private boolean tocou(Rectangle jogador) {
            return tempoAntesDaColeta <= 0
                    && getBoundsInParent().intersects(
                    jogador.getBoundsInParent()
            );
        }
    }

    private record ItensPerdidos(
            String mapa,
            Point2D posicao,
            int shurikens,
            int bombasFumaca,
            int kitsMedicos,
            boolean katana
    ) {
        private boolean vazio() {
            return shurikens == 0
                    && bombasFumaca == 0
                    && kitsMedicos == 0
                    && !katana;
        }
    }

    /** Bolsa deixada no cenário quando o jogador é derrotado. */
    private static final class BolsaItensPerdidos extends Pane {
        private final ItensPerdidos conteudo;
        private double tempoAnimacao;

        private BolsaItensPerdidos(ItensPerdidos conteudo) {
            this.conteudo = conteudo;
            setLayoutX(conteudo.posicao().getX() - 22);
            setLayoutY(conteudo.posicao().getY() - 22);
            setPrefSize(44, 44);
            setManaged(false);
            setMouseTransparent(true);

            Circle brilho = new Circle(22, 22, 20);
            brilho.setFill(Color.rgb(255, 190, 55, 0.20));
            brilho.setStroke(Color.rgb(255, 220, 105, 0.88));
            brilho.setStrokeWidth(2);

            Rectangle bolsa = new Rectangle(10, 13, 24, 24);
            bolsa.setArcWidth(9);
            bolsa.setArcHeight(9);
            bolsa.setFill(Color.web("#8a4d2f"));
            bolsa.setStroke(Color.web("#e5b45f"));
            bolsa.setStrokeWidth(2);

            Line cordao = new Line(13, 15, 31, 15);
            cordao.setStroke(Color.web("#f1d79a"));
            cordao.setStrokeWidth(3);

            Label marca = new Label("!");
            marca.setLayoutX(19);
            marca.setLayoutY(15);
            marca.setStyle(
                    "-fx-text-fill: #fff0a6; -fx-font-weight: bold;"
            );
            getChildren().addAll(brilho, bolsa, cordao, marca);
        }

        private void atualizar(double tempo) {
            tempoAnimacao += tempo;
            setTranslateY(-3 + Math.sin(tempoAnimacao * 3.8) * 3);
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

    private enum AcaoControle {
        CIMA("Mover para cima", "cima", KeyCode.W),
        BAIXO("Mover para baixo", "baixo", KeyCode.S),
        ESQUERDA("Mover para esquerda", "esquerda", KeyCode.A),
        DIREITA("Mover para direita", "direita", KeyCode.D),
        FURTIVO("Andar silenciosamente", "furtivo", KeyCode.SHIFT),
        INTERAGIR("Interagir / entrar", "interagir", KeyCode.E),
        SHURIKEN("Lançar shuriken", "shuriken", KeyCode.SPACE),
        FUMACA("Bomba de fumaça", "fumaca", KeyCode.Q),
        KIT_MEDICO("Usar kit médico", "kit", KeyCode.H),
        KATANA("Usar katana", "katana", KeyCode.F),
        REINICIAR("Reiniciar após derrota", "reiniciar", KeyCode.R),
        PULAR_TUTORIAL("Pular tutorial", "pular_tutorial", KeyCode.T),
        DEPURACAO("Mostrar caminhos (dev)", "depuracao", KeyCode.F3);

        private final String descricao;
        private final String chave;
        private final KeyCode padrao;

        AcaoControle(String descricao, String chave, KeyCode padrao) {
            this.descricao = descricao;
            this.chave = chave;
            this.padrao = padrao;
        }
    }

    private record OpcaoFase(
            String nome,
            String mapa,
            String pontoSaida,
            boolean armazem
    ) {
        @Override
        public String toString() {
            return nome;
        }
    }

    private static final List<OpcaoFase> FASES_DESENVOLVIMENTO = List.of(
            new OpcaoFase(
                    "1 — Entrada do labirinto",
                    "entrada_labirinto.tmx", "saida_escadaria", false
            ),
            new OpcaoFase(
                    "2 — Armazém do dojo",
                    "armazem_dojo.tmx", "saida_armazem", true
            ),
            new OpcaoFase(
                    "3 — Dojo dos guardas",
                    "dojo_guardas.tmx", "saida_dojo", false
            ),
            new OpcaoFase(
                    "4 — Sala de treinamento",
                    "sala_treinamento.tmx", "saida_treinamento", false
            ),
            new OpcaoFase(
                    "Sala lateral — depósito",
                    "sala_lateral_deposito.tmx", "saida_sala_lateral", false
            ),
            new OpcaoFase(
                    "Sala lateral — guarda",
                    "sala_lateral_guarda.tmx", "saida_sala_lateral", false
            ),
            new OpcaoFase(
                    "Sala lateral — vazia",
                    "sala_lateral_vazia.tmx", "saida_sala_lateral", false
            ),
            new OpcaoFase(
                    "5 — Pátio noturno",
                    "patio_noturno.tmx", "saida_patio", false
            ),
            new OpcaoFase(
                    "6 — Arena do subchefe",
                    "arena_subchefe.tmx", "saida_arena", false
            )
    );

    /**
     * Método executado quando o JavaFX é iniciado.
     *
     * @param janela janela principal do jogo.
     */
    @Override
    public void start(Stage janela) {
        carregarAtalhos();
        carregarVolumeEfeitos();
        mostrarTelaInicial(janela);
    }

    private void mostrarTelaInicial(Stage janela) {
        teclasPressionadas.clear();

        StackPane raiz = new StackPane();
        raiz.setPrefSize(LARGURA_JANELA, ALTURA_JANELA);
        raiz.setStyle(
                "-fx-background-color: linear-gradient(to bottom, "
                        + "#11131b 0%, #211617 58%, #0c0c11 100%);"
        );

        Canvas fundo = criarFundoMenu();
        fundo.setMouseTransparent(true);

        Label titulo = new Label("HANGETSU");
        titulo.setStyle(
                "-fx-text-fill: #f1d79a;"
                        + "-fx-font-size: 54px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-effect: dropshadow(gaussian, #000000, 12, 0.6, 0, 4);"
        );

        Label subtitulo = new Label("SHADOW DOJO");
        subtitulo.setStyle(
                "-fx-text-fill: #c54b43;"
                        + "-fx-font-size: 22px;"
                        + "-fx-font-weight: bold;"
        );

        Button iniciar = criarBotaoMenu("INICIAR JOGO");
        Button continuar = criarBotaoMenu("CONTINUAR");
        Button controles = criarBotaoMenu("CONTROLES");
        Button configuracoes = criarBotaoMenu("CONFIGURAÇÕES");
        Button sair = criarBotaoMenu("SAIR");
        continuar.setDisable(!possuiProgressoSalvo());

        Pane confirmacaoNovoJogo = new Pane();
        confirmacaoNovoJogo.setPrefSize(LARGURA_JANELA, ALTURA_JANELA);
        confirmacaoNovoJogo.setStyle(
                "-fx-background-color: rgba(5, 6, 10, 0.82);"
        );
        confirmacaoNovoJogo.setVisible(false);

        Label textoConfirmacao = new Label(
                "Começar um novo jogo apagará o checkpoint do subchefe."
        );
        textoConfirmacao.setWrapText(true);
        textoConfirmacao.setMaxWidth(380);
        textoConfirmacao.setStyle(
                "-fx-text-fill: #f4e5c1; -fx-font-size: 17px;"
        );
        Button confirmarNovoJogo = criarBotaoMenu("SIM, NOVO JOGO");
        Button cancelarNovoJogo = criarBotaoMenu("CANCELAR");
        VBox caixaConfirmacao = new VBox(
                14, textoConfirmacao, confirmarNovoJogo, cancelarNovoJogo
        );
        caixaConfirmacao.setAlignment(Pos.CENTER);
        caixaConfirmacao.setLayoutX((LARGURA_JANELA - 480) / 2.0);
        caixaConfirmacao.setLayoutY(170);
        caixaConfirmacao.setPrefWidth(480);
        caixaConfirmacao.setPadding(new Insets(28));
        caixaConfirmacao.setStyle(
                "-fx-background-color: rgba(25, 18, 20, 0.98);"
                        + "-fx-border-color: #b9854d;"
                        + "-fx-border-radius: 10px;"
                        + "-fx-background-radius: 10px;"
        );
        confirmacaoNovoJogo.getChildren().add(caixaConfirmacao);

        Runnable comecarNovoJogo = () -> {
            apagarProgressoSalvo();
            reiniciarEstadoJogo();
            iniciarFase(
                    janela,
                    "entrada_labirinto.tmx",
                    "saida_escadaria",
                    false,
                    100
            );
        };

        iniciar.setOnAction(evento -> {
            if (possuiProgressoSalvo()) {
                confirmacaoNovoJogo.setVisible(true);
            } else {
                comecarNovoJogo.run();
            }
        });
        confirmarNovoJogo.setOnAction(evento -> comecarNovoJogo.run());
        cancelarNovoJogo.setOnAction(evento ->
                confirmacaoNovoJogo.setVisible(false)
        );

        continuar.setOnAction(evento -> continuarJogoSalvo(janela));
        controles.setOnAction(evento -> mostrarTelaControles(janela));
        configuracoes.setOnAction(evento ->
                mostrarTelaConfiguracoes(janela)
        );
        sair.setOnAction(evento -> janela.close());

        VBox botoesPrincipais = new VBox(
                8, iniciar, continuar, controles, configuracoes, sair
        );
        botoesPrincipais.setAlignment(Pos.CENTER);

        ComboBox<OpcaoFase> seletorFase = new ComboBox<>();
        seletorFase.getItems().addAll(FASES_DESENVOLVIMENTO);
        seletorFase.getSelectionModel().selectFirst();
        seletorFase.setPrefWidth(270);
        seletorFase.setStyle(
                "-fx-font-size: 14px;"
                        + "-fx-background-color: #e7dcc3;"
        );

        Button iniciarFaseEscolhida = criarBotaoMenu("TESTAR FASE");
        iniciarFaseEscolhida.setPrefWidth(150);
        iniciarFaseEscolhida.setOnAction(evento -> {
            OpcaoFase opcao = seletorFase.getValue();

            if (opcao != null) {
                prepararTesteDesenvolvimento();
                reiniciarTutorialDoMapa(opcao.mapa());
                iniciarFase(
                        janela,
                        opcao.mapa(),
                        opcao.pontoSaida(),
                        opcao.armazem(),
                        100
                );
            }
        });

        Label tituloDesenvolvimento = new Label("MODO DE DESENVOLVIMENTO");
        tituloDesenvolvimento.setStyle(
                "-fx-text-fill: #8fe8ff;"
                        + "-fx-font-size: 13px;"
                        + "-fx-font-weight: bold;"
        );
        HBox seletor = new HBox(10, seletorFase, iniciarFaseEscolhida);
        seletor.setAlignment(Pos.CENTER);

        VBox painelDesenvolvimento = new VBox(
                9,
                tituloDesenvolvimento,
                seletor
        );
        painelDesenvolvimento.setAlignment(Pos.CENTER);
        painelDesenvolvimento.setPadding(new Insets(14));
        painelDesenvolvimento.setStyle(
                "-fx-background-color: rgba(8, 35, 55, 0.88);"
                        + "-fx-border-color: rgba(110, 220, 255, 0.55);"
                        + "-fx-border-radius: 8px;"
                        + "-fx-background-radius: 8px;"
        );

        VBox menu = new VBox(
                8,
                titulo,
                subtitulo,
                new Label(" "),
                botoesPrincipais,
                new Label(" "),
                painelDesenvolvimento
        );
        menu.setAlignment(Pos.CENTER);
        menu.setMaxWidth(560);
        menu.setPadding(new Insets(26));

        raiz.getChildren().addAll(fundo, menu, confirmacaoNovoJogo);
        Scene cena = new Scene(raiz, LARGURA_JANELA, ALTURA_JANELA);

        janela.setTitle("Hangetsu: Shadow Dojo");
        janela.setScene(cena);
        janela.setResizable(false);
        janela.show();
    }

    private void mostrarTelaCheckpointSubchefe(Stage janela) {
        teclasPressionadas.clear();

        StackPane raiz = new StackPane();
        raiz.setPrefSize(LARGURA_JANELA, ALTURA_JANELA);
        raiz.setStyle(
                "-fx-background-color: linear-gradient(to bottom, "
                        + "#11131b 0%, #2b1b18 62%, #0c0c11 100%);"
        );

        Label titulo = new Label("JORNADA CONCLUÍDA");
        titulo.setStyle(
                "-fx-text-fill: #f1d79a;"
                        + "-fx-font-size: 38px;"
                        + "-fx-font-weight: bold;"
        );
        Label resultado = new Label(
                "O mestre da arena foi derrotado.\n"
                        + "A paz voltou ao dojo por esta noite."
        );
        resultado.setTextAlignment(TextAlignment.CENTER);
        resultado.setStyle(
                "-fx-text-fill: #eee4d0; -fx-font-size: 18px;"
        );
        Label inventarioSalvo = new Label(
                "Katana: "
                        + (inventarioKatana ? "desbloqueada" : "não coletada")
                        + "\n"
                        + "Shurikens: " + inventarioShurikens
                        + "  |  Fumaça: " + inventarioBombasFumaca
                        + "  |  Kits: " + inventarioKitsMedicos
        );
        inventarioSalvo.setTextAlignment(TextAlignment.CENTER);
        inventarioSalvo.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.30);"
                        + "-fx-text-fill: #8fe8ff;"
                        + "-fx-font-size: 16px;"
                        + "-fx-padding: 18px 28px;"
                        + "-fx-background-radius: 8px;"
        );

        Button voltarMenu = criarBotaoMenu("VOLTAR AO MENU");
        voltarMenu.setOnAction(evento -> mostrarTelaInicial(janela));

        VBox conteudo = new VBox(
                22, titulo, resultado, inventarioSalvo, voltarMenu
        );
        conteudo.setAlignment(Pos.CENTER);
        conteudo.setMaxWidth(620);
        conteudo.setPadding(new Insets(42));
        conteudo.setStyle(
                "-fx-background-color: rgba(24, 18, 20, 0.94);"
                        + "-fx-border-color: #b9854d;"
                        + "-fx-border-radius: 12px;"
                        + "-fx-background-radius: 12px;"
        );
        raiz.getChildren().add(conteudo);

        janela.setTitle("Hangetsu: jornada concluída");
        janela.setScene(new Scene(raiz, LARGURA_JANELA, ALTURA_JANELA));
        janela.setResizable(false);
        janela.show();
    }

    private Canvas criarFundoMenu() {
        Canvas fundo = new Canvas(LARGURA_JANELA, ALTURA_JANELA);
        GraphicsContext pincel = fundo.getGraphicsContext2D();

        pincel.setFill(Color.rgb(20, 13, 15, 0.82));
        pincel.fillRect(0, 0, LARGURA_JANELA, ALTURA_JANELA);

        // Estrutura de madeira e painéis shoji provisórios.
        pincel.setFill(Color.web("#3a211d"));
        pincel.fillRect(70, 55, 820, 26);
        pincel.fillRect(70, 540, 820, 28);
        pincel.fillRect(70, 55, 24, 513);
        pincel.fillRect(866, 55, 24, 513);

        pincel.setFill(Color.rgb(225, 205, 162, 0.12));
        pincel.fillRect(94, 81, 210, 459);
        pincel.fillRect(656, 81, 210, 459);
        pincel.setStroke(Color.rgb(133, 77, 52, 0.55));
        pincel.setLineWidth(5);

        for (int x : new int[]{94, 656}) {
            for (int coluna = 1; coluna < 5; coluna++) {
                double linhaX = x + coluna * 42;
                pincel.strokeLine(linhaX, 81, linhaX, 540);
            }
            for (int linha = 1; linha < 9; linha++) {
                double linhaY = 81 + linha * 51;
                pincel.strokeLine(x, linhaY, x + 210, linhaY);
            }
        }

        // Lanternas provisórias nas laterais do menu.
        for (double x : new double[]{118, 818}) {
            pincel.setFill(Color.rgb(255, 164, 54, 0.16));
            pincel.fillOval(x - 42, 244, 84, 110);
            pincel.setFill(Color.web("#ffb64f"));
            pincel.fillRoundRect(x - 16, 270, 32, 56, 9, 9);
            pincel.setStroke(Color.web("#6b2d1f"));
            pincel.setLineWidth(5);
            pincel.strokeRoundRect(x - 16, 270, 32, 56, 9, 9);
        }

        return fundo;
    }

    private Button criarBotaoMenu(String texto) {
        Button botao = new Button(texto);
        botao.setPrefSize(300, 43);
        botao.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #6e3f2b, #3b211b);"
                        + "-fx-text-fill: #f4e5c1;"
                        + "-fx-font-size: 15px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-border-color: #b9854d;"
                        + "-fx-border-width: 1px;"
                        + "-fx-border-radius: 5px;"
                        + "-fx-background-radius: 5px;"
                        + "-fx-cursor: hand;"
        );
        return botao;
    }

    private void mostrarTelaControles(Stage janela) {
        VBox conteudo = new VBox(18);
        conteudo.setAlignment(Pos.CENTER);
        conteudo.setPadding(new Insets(48));
        conteudo.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #17141b, #281918);"
        );

        Label titulo = new Label("CONTROLES");
        titulo.setStyle(
                "-fx-text-fill: #f1d79a;"
                        + "-fx-font-size: 38px;"
                        + "-fx-font-weight: bold;"
        );
        Label lista = new Label(
                nomeTecla(AcaoControle.CIMA) + ", "
                        + nomeTecla(AcaoControle.ESQUERDA) + ", "
                        + nomeTecla(AcaoControle.BAIXO) + ", "
                        + nomeTecla(AcaoControle.DIREITA)
                        + " — movimentar\n"
                        + nomeTecla(AcaoControle.FURTIVO)
                        + " — andar silenciosamente\n"
                        + nomeTecla(AcaoControle.SHURIKEN)
                        + " — lançar shuriken\n"
                        + nomeTecla(AcaoControle.FUMACA)
                        + " — bomba de fumaça\n"
                        + nomeTecla(AcaoControle.KIT_MEDICO)
                        + " — usar kit médico\n"
                        + nomeTecla(AcaoControle.KATANA)
                        + " — usar katana\n"
                        + nomeTecla(AcaoControle.INTERAGIR)
                        + " — interagir e atravessar portas\n"
                        + nomeTecla(AcaoControle.DEPURACAO)
                        + " — mostrar caminhos e colisões do A*"
        );
        lista.setStyle(
                "-fx-text-fill: #eee4d0;"
                        + "-fx-font-size: 18px;"
                        + "-fx-line-spacing: 8px;"
                        + "-fx-padding: 24px;"
                        + "-fx-background-color: rgba(0, 0, 0, 0.30);"
                        + "-fx-background-radius: 10px;"
        );

        Button editarAtalhos = criarBotaoMenu("EDITAR ATALHOS");
        editarAtalhos.setOnAction(evento ->
                mostrarTelaConfiguracoes(
                        janela,
                        () -> mostrarTelaControles(janela)
                )
        );
        Button voltar = criarBotaoMenu("VOLTAR");
        voltar.setOnAction(evento -> mostrarTelaInicial(janela));
        conteudo.getChildren().addAll(
                titulo, lista, editarAtalhos, voltar
        );

        janela.setScene(new Scene(conteudo, LARGURA_JANELA, ALTURA_JANELA));
    }

    private void mostrarTelaConfiguracoes(Stage janela) {
        mostrarTelaConfiguracoes(
                janela,
                () -> mostrarTelaInicial(janela)
        );
    }

    private void mostrarTelaConfiguracoes(
            Stage janela,
            Runnable aoVoltar
    ) {
        VBox conteudo = new VBox(14);
        conteudo.setAlignment(Pos.CENTER);
        conteudo.setPadding(new Insets(24));
        conteudo.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #17141b, #281918);"
        );

        Label titulo = new Label("CONFIGURAÇÕES — CONTROLES");
        titulo.setStyle(
                "-fx-text-fill: #f1d79a;"
                        + "-fx-font-size: 32px;"
                        + "-fx-font-weight: bold;"
        );
        Label instrucao = new Label(
                "Clique em um atalho e pressione a nova tecla. ESC cancela."
        );
        instrucao.setStyle("-fx-text-fill: #d8cdb8; -fx-font-size: 15px;");

        Label textoVolume = new Label(
                "Volume dos efeitos: " + Math.round(volumeEfeitos * 100) + "%"
        );
        textoVolume.setStyle(
                "-fx-text-fill: #eee4d0; -fx-font-size: 15px;"
        );
        Slider controleVolume = new Slider(0, 100, volumeEfeitos * 100);
        controleVolume.setPrefWidth(330);
        controleVolume.setShowTickMarks(true);
        controleVolume.setMajorTickUnit(25);
        controleVolume.valueProperty().addListener(
                (observavel, anterior, atual) -> {
                    volumeEfeitos = atual.doubleValue() / 100.0;
                    textoVolume.setText(
                            "Volume dos efeitos: "
                                    + Math.round(atual.doubleValue()) + "%"
                    );
                    salvarVolumeEfeitos();
                }
        );
        VBox painelVolume = new VBox(5, textoVolume, controleVolume);
        painelVolume.setAlignment(Pos.CENTER);

        GridPane colunaEsquerda = criarGradeAtalhos();
        GridPane colunaDireita = criarGradeAtalhos();
        Map<AcaoControle, Button> botoes =
                new EnumMap<>(AcaoControle.class);
        AcaoControle[] aguardando = {null};
        Button[] botaoAguardando = {null};
        Label estado = new Label("Nenhuma alteração pendente.");
        estado.setStyle("-fx-text-fill: #8fe8ff; -fx-font-size: 14px;");

        AcaoControle[] acoes = AcaoControle.values();
        for (int indice = 0; indice < acoes.length; indice++) {
            AcaoControle acao = acoes[indice];
            GridPane grade = indice < 7 ? colunaEsquerda : colunaDireita;
            int linha = indice < 7 ? indice : indice - 7;

            Label descricao = new Label(acao.descricao);
            descricao.setStyle(
                    "-fx-text-fill: #eee4d0; -fx-font-size: 14px;"
            );
            Button botao = new Button(nomeTecla(acao));
            botao.setPrefWidth(145);
            botao.setStyle(
                    "-fx-background-color: #4b3026;"
                            + "-fx-text-fill: #ffe4a3;"
                            + "-fx-border-color: #a97849;"
                            + "-fx-font-weight: bold;"
            );
            botao.setOnAction(evento -> {
                if (botaoAguardando[0] != null) {
                    botaoAguardando[0].setText(
                            nomeTecla(aguardando[0])
                    );
                }
                aguardando[0] = acao;
                botaoAguardando[0] = botao;
                botao.setText("Pressione...");
                estado.setText("Definindo: " + acao.descricao);
            });
            botoes.put(acao, botao);
            grade.add(descricao, 0, linha);
            grade.add(botao, 1, linha);
        }

        HBox grades = new HBox(32, colunaEsquerda, colunaDireita);
        grades.setAlignment(Pos.CENTER);

        Button restaurar = criarBotaoMenu("RESTAURAR PADRÕES");
        restaurar.setPrefWidth(220);
        restaurar.setOnAction(evento -> {
            for (AcaoControle acao : AcaoControle.values()) {
                atalhos.put(acao, acao.padrao);
                botoes.get(acao).setText(nomeTecla(acao));
            }
            salvarAtalhos();
            aguardando[0] = null;
            botaoAguardando[0] = null;
            estado.setText("Controles padrão restaurados.");
        });

        Button voltar = criarBotaoMenu("VOLTAR");
        voltar.setPrefWidth(220);
        voltar.setOnAction(evento -> aoVoltar.run());
        HBox botoesRodape = new HBox(12, restaurar, voltar);
        botoesRodape.setAlignment(Pos.CENTER);

        conteudo.getChildren().addAll(
                titulo, instrucao, painelVolume, grades, estado, botoesRodape
        );
        Scene cena = new Scene(conteudo, LARGURA_JANELA, ALTURA_JANELA);
        cena.setOnKeyPressed(evento -> {
            if (aguardando[0] == null) {
                if (evento.getCode() == KeyCode.ESCAPE) {
                    aoVoltar.run();
                    evento.consume();
                }
                return;
            }

            if (evento.getCode() == KeyCode.ESCAPE) {
                botaoAguardando[0].setText(nomeTecla(aguardando[0]));
                aguardando[0] = null;
                botaoAguardando[0] = null;
                estado.setText("Alteração cancelada.");
                evento.consume();
                return;
            }

            AcaoControle conflito = null;
            for (AcaoControle acao : AcaoControle.values()) {
                if (acao != aguardando[0]
                        && tecla(acao) == evento.getCode()) {
                    conflito = acao;
                    break;
                }
            }

            if (conflito != null) {
                estado.setText(
                        "Essa tecla já controla: " + conflito.descricao
                );
                botaoAguardando[0].setText(nomeTecla(aguardando[0]));
            } else {
                AcaoControle acaoAlterada = aguardando[0];
                atalhos.put(acaoAlterada, evento.getCode());
                botaoAguardando[0].setText(nomeTecla(acaoAlterada));
                salvarAtalhos();
                estado.setText(
                        acaoAlterada.descricao + " alterado para "
                                + evento.getCode().getName() + "."
                );
            }

            aguardando[0] = null;
            botaoAguardando[0] = null;
            evento.consume();
        });
        janela.setScene(cena);
    }

    private GridPane criarGradeAtalhos() {
        GridPane grade = new GridPane();
        grade.setHgap(12);
        grade.setVgap(8);
        grade.setPadding(new Insets(15));
        grade.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.28);"
                        + "-fx-background-radius: 10px;"
        );
        return grade;
    }

    private void carregarAtalhos() {
        atalhos.clear();

        try {
            for (AcaoControle acao : AcaoControle.values()) {
                String valor = PREFERENCIAS.get(
                        "controle." + acao.chave,
                        acao.padrao.name()
                );
                try {
                    atalhos.put(acao, KeyCode.valueOf(valor));
                } catch (IllegalArgumentException excecao) {
                    atalhos.put(acao, acao.padrao);
                }
            }
        } catch (SecurityException excecao) {
            for (AcaoControle acao : AcaoControle.values()) {
                atalhos.put(acao, acao.padrao);
            }
        }

        if (new HashSet<>(atalhos.values()).size()
                != AcaoControle.values().length) {
            for (AcaoControle acao : AcaoControle.values()) {
                atalhos.put(acao, acao.padrao);
            }
            salvarAtalhos();
        }
    }

    private void salvarAtalhos() {
        try {
            for (AcaoControle acao : AcaoControle.values()) {
                PREFERENCIAS.put(
                        "controle." + acao.chave,
                        tecla(acao).name()
                );
            }
        } catch (SecurityException excecao) {
            // As teclas continuam válidas durante a execução atual.
        }
    }

    private KeyCode tecla(AcaoControle acao) {
        return atalhos.getOrDefault(acao, acao.padrao);
    }

    private String nomeTecla(AcaoControle acao) {
        return tecla(acao).getName().toUpperCase();
    }

    private void carregarVolumeEfeitos() {
        try {
            volumeEfeitos = Math.max(0, Math.min(
                    1,
                    PREFERENCIAS.getDouble(CHAVE_VOLUME_EFEITOS, 0.75)
            ));
        } catch (SecurityException excecao) {
            volumeEfeitos = 0.75;
        }
        somPassos.definirVolume(volumeEfeitos);
    }

    private void salvarVolumeEfeitos() {
        somPassos.definirVolume(volumeEfeitos);
        try {
            PREFERENCIAS.putDouble(
                    CHAVE_VOLUME_EFEITOS,
                    volumeEfeitos
            );
        } catch (SecurityException excecao) {
            // O volume continua válido durante a execução atual.
        }
    }

    private void reiniciarEstadoJogo() {
        inventarioShurikens = 0;
        inventarioBombasFumaca = 0;
        inventarioKitsMedicos = 0;
        inventarioKatana = false;
        bausAbertosPersistentes.clear();
        itensPerdidos.clear();
        limparRetornoSalaLateral();
    }

    private void prepararTesteDesenvolvimento() {
        reiniciarEstadoJogo();
        inventarioShurikens = 18;
        inventarioBombasFumaca = 4;
        inventarioKitsMedicos = 2;
        // Facilita testar imediatamente a animação e o alcance do golpe.
        inventarioKatana = true;
    }

    private void limparRetornoSalaLateral() {
        mapaRetornoSalaLateral = null;
        pontoSaidaRetornoSalaLateral = null;
        armazemRetornoSalaLateral = false;
        spawnRetornoSalaLateral = null;
    }

    private boolean possuiProgressoSalvo() {
        try {
            return PREFERENCIAS.getBoolean(
                    CHAVE_CHECKPOINT_SUBCHEFE, false
            );
        } catch (SecurityException excecao) {
            return false;
        }
    }

    private boolean tutorialJaConcluido() {
        try {
            return PREFERENCIAS.getBoolean(CHAVE_TUTORIAL, false);
        } catch (SecurityException excecao) {
            return false;
        }
    }

    private void marcarTutorialConcluido() {
        try {
            PREFERENCIAS.putBoolean(CHAVE_TUTORIAL, true);
        } catch (SecurityException excecao) {
            // O tutorial apenas não será lembrado na próxima execução.
        }
    }

    private boolean tutorialItensJaConcluido() {
        try {
            return PREFERENCIAS.getBoolean(
                    CHAVE_TUTORIAL_ITENS, false
            );
        } catch (SecurityException excecao) {
            return false;
        }
    }

    private void marcarTutorialItensConcluido() {
        try {
            PREFERENCIAS.putBoolean(CHAVE_TUTORIAL_ITENS, true);
        } catch (SecurityException excecao) {
            // O tutorial apenas não será lembrado na próxima execução.
        }
    }

    private void continuarJogoSalvo(Stage janela) {
        try {
            if (!possuiProgressoSalvo()) {
                mostrarTelaInicial(janela);
                return;
            }

            inventarioShurikens = PREFERENCIAS.getInt(CHAVE_SHURIKENS, 0);
            inventarioBombasFumaca = PREFERENCIAS.getInt(CHAVE_FUMACA, 0);
            inventarioKitsMedicos = PREFERENCIAS.getInt(CHAVE_KITS, 0);
            inventarioKatana = PREFERENCIAS.getBoolean(CHAVE_KATANA, false);
            limparRetornoSalaLateral();
            mostrarTelaCheckpointSubchefe(janela);
        } catch (SecurityException excecao) {
            mostrarTelaInicial(janela);
        }
    }

    private void salvarCheckpointSubchefe(
            int shurikens,
            int bombasFumaca,
            int kitsMedicos
    ) {
        inventarioShurikens = shurikens;
        inventarioBombasFumaca = bombasFumaca;
        inventarioKitsMedicos = kitsMedicos;
        try {
            PREFERENCIAS.putBoolean(CHAVE_CHECKPOINT_SUBCHEFE, true);
            salvarInventarioNasPreferencias();
        } catch (SecurityException excecao) {
            // O jogo continua normalmente mesmo se o sistema bloquear o save.
        }
    }

    private void apagarProgressoSalvo() {
        try {
            PREFERENCIAS.remove(CHAVE_CHECKPOINT_SUBCHEFE);
            PREFERENCIAS.remove(CHAVE_MAPA);
            PREFERENCIAS.remove(CHAVE_SAIDA);
            PREFERENCIAS.remove(CHAVE_ARMAZEM);
            PREFERENCIAS.remove(CHAVE_VIDA);
            PREFERENCIAS.remove(CHAVE_SHURIKENS);
            PREFERENCIAS.remove(CHAVE_FUMACA);
            PREFERENCIAS.remove(CHAVE_KITS);
            PREFERENCIAS.remove(CHAVE_KATANA);
            PREFERENCIAS.remove(CHAVE_TUTORIAL);
            PREFERENCIAS.remove(CHAVE_TUTORIAL_ITENS);
        } catch (SecurityException excecao) {
            // Um novo jogo ainda pode começar na sessão atual.
        }
    }

    /** No modo de desenvolvimento, testar uma fase sempre reapresenta seu tutorial. */
    private void reiniciarTutorialDoMapa(String nomeMapa) {
        try {
            if ("entrada_labirinto.tmx".equals(nomeMapa)) {
                PREFERENCIAS.remove(CHAVE_TUTORIAL);
            } else if ("armazem_dojo.tmx".equals(nomeMapa)) {
                PREFERENCIAS.remove(CHAVE_TUTORIAL_ITENS);
            }
        } catch (SecurityException excecao) {
            // O teste da fase continua mesmo se as preferências forem bloqueadas.
        }
    }

    private void salvarInventarioNasPreferencias() {
        try {
            PREFERENCIAS.putInt(CHAVE_SHURIKENS, inventarioShurikens);
            PREFERENCIAS.putInt(CHAVE_FUMACA, inventarioBombasFumaca);
            PREFERENCIAS.putInt(CHAVE_KITS, inventarioKitsMedicos);
            PREFERENCIAS.putBoolean(CHAVE_KATANA, inventarioKatana);
        } catch (SecurityException excecao) {
            // O inventário em memória continua funcionando nesta execução.
        }
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
        int[] indiceVisualGuarda = {0};

        carregadorMapa.obterRotasGuardas().forEach((nome, rota) -> {
            GuardaPatrulha guarda = new GuardaPatrulha(rota);
            guarda.setId(nome);
            guarda.definirTipoVisual(
                    indiceVisualGuarda[0]++ % 2 == 0
                            ? GuardaPatrulha.TipoVisual.COMUM
                            : GuardaPatrulha.TipoVisual.DISTANCIA
            );
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
        List<BolsaItensPerdidos> bolsasItensPerdidos = new ArrayList<>();

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
        int[] acaoVisualJogador = {0};
        int[] quadroVisualJogador = {0};
        double[] tempoQuadroJogador = {0};
        double[] tempoAcaoJogador = {0};
        double[] tempoDanoVisualJogador = {0};
        double[] tempoProtecaoAtaqueCorpoACorpo = {0};
        Circle[] nuvemFumaca = {null};
        double[] tempoFumaca = {0};
        double[] tempoMensagemColeta = {0};
        double[] tempoSemVerJogador = {0};
        Point2D[] ultimaPosicaoVista = {new Point2D(0, 0)};
        double[] tempoAteProximoPasso = {0};
        double[] tempoPassoAudivel = {0};
        double[] alcanceUltimoPasso = {0};
        double[] tempoIndicadorRuido = {0};
        double[] raioVisualUltimoRuido = {44};
        Point2D[] posicaoUltimoPasso = {new Point2D(0, 0)};
        GuardaPatrulha[] guardaInvestigando = {null};
        double[] tempoInvestigandoLocal = {0};
        boolean[] passoJaInvestigado = {true};
        boolean[] ruidoDeItemNesteQuadro = {false};
        boolean tutorialDeItens = "armazem_dojo.tmx".equals(nomeMapa)
                && !tutorialItensJaConcluido();
        boolean[] tutorialAtivo = {
                ("entrada_labirinto.tmx".equals(nomeMapa)
                        && !tutorialJaConcluido()) || tutorialDeItens
        };
        int[] etapaTutorial = {0};
        double[] tempoEtapaTutorial = {0};
        int totalItensInicioTutorial = inventarioShurikens
                + inventarioBombasFumaca + inventarioKitsMedicos;
        Point2D[] pontoInicioTutorial = {new Point2D(0, 0)};

        /*
         * Cria um jogador provisório.
         *
         * Ele possui 32 pixels de largura e 32 de altura,
         * o mesmo tamanho dos blocos utilizados no Tiled.
         */
        Rectangle jogador = new Rectangle(32, 32);

        // O retângulo continua responsável apenas por colisão e combate.
        jogador.setFill(Color.TRANSPARENT);

        Image spriteCaminhadaJogador = carregarSpriteJogador(
                "/sprites/ninja_caminhada_alinhado.png"
        );
        Image spriteKatanaJogador = carregarSpriteJogador(
                "/sprites/ninja_katana_alinhado.png"
        );
        Image spriteShurikenJogador = carregarSpriteJogador(
                "/sprites/ninja_shuriken_alinhado.png"
        );
        ImageView visualJogador = criarVisualJogador(
                jogador,
                spriteCaminhadaJogador
        );
        Circle indicadorRuidoPasso = new Circle();
        indicadorRuidoPasso.setFill(Color.TRANSPARENT);
        indicadorRuidoPasso.setStroke(Color.rgb(245, 205, 110, 0.70));
        indicadorRuidoPasso.setStrokeWidth(2);
        indicadorRuidoPasso.setVisible(false);
        indicadorRuidoPasso.setMouseTransparent(true);

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
        pontoInicioTutorial[0] = centroDoJogador(jogador);

        // A imagem acompanha a caixa de colisão sem alterar seu tamanho.
        mundo.getChildren().addAll(
                indicadorRuidoPasso,
                jogador,
                visualJogador
        );

        for (ItensPerdidos conteudo : itensPerdidos) {
            if (nomeMapa.equals(conteudo.mapa())) {
                BolsaItensPerdidos bolsa =
                        new BolsaItensPerdidos(conteudo);
                bolsasItensPerdidos.add(bolsa);
                mundo.getChildren().add(bolsa);
            }
        }

        // Camada transparente ativada com F3 para inspecionar a navegação.
        Canvas depuracaoNavegacao = new Canvas(
                visualMapa.getWidth(),
                visualMapa.getHeight()
        );
        depuracaoNavegacao.setVisible(false);
        depuracaoNavegacao.setMouseTransparent(true);
        mundo.getChildren().add(depuracaoNavegacao);

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
                        ? "Entrada da arena — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para continuar"
                        : nomeMapa.startsWith("sala_lateral_")
                        ? "Voltar pela porta — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR)
                        : "sala_treinamento.tmx".equals(nomeMapa)
                        ? "Entrada do pátio — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para continuar"
                        : "dojo_guardas.tmx".equals(nomeMapa)
                        ? "Entrada da sala de treino — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para continuar"
                        : armazem
                        ? "Saída encontrada — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para continuar"
                        : "Escadaria encontrada — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para entrar";
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


        Label avisoBau = new Label(
                "Pressione " + nomeTecla(AcaoControle.INTERAGIR)
                        + " para abrir o baú"
        );
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

        PainelInventario indicadorInventario =
                new PainelInventario(arenaSubchefe);
        indicadorInventario.atualizar(
                shurikens[0],
                bombasFumaca[0],
                kitsMedicos[0],
                inventarioKatana
        );
        indicadorInventario.setLayoutX(LARGURA_JANELA - 334);
        indicadorInventario.setLayoutY(153);
        // Depois que o jogador chega ao armazém (sala 2), o inventário
        // acompanha todas as fases e salas laterais seguintes.
        boolean mostrarInventario =
                !"entrada_labirinto.tmx".equals(nomeMapa);
        indicadorInventario.setVisible(false);

        Button botaoMochila = criarBotaoMochila();
        botaoMochila.setLayoutX(LARGURA_JANELA - 88);
        botaoMochila.setLayoutY(76);
        botaoMochila.setVisible(mostrarInventario);
        botaoMochila.setManaged(false);
        botaoMochila.setOnAction(evento -> indicadorInventario.setVisible(
                mostrarInventario && !indicadorInventario.isVisible()
        ));

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

        Label avisoTutorial = new Label(
                tutorialDeItens
                        ? textoEtapaTutorialItens(0)
                        : textoEtapaTutorial(0)
        );
        avisoTutorial.setWrapText(true);
        avisoTutorial.setAlignment(Pos.CENTER);
        avisoTutorial.setPrefWidth(680);
        avisoTutorial.setStyle(
                "-fx-background-color: rgba(17, 30, 38, 0.94);"
                        + "-fx-text-fill: #f3e4b8;"
                        + "-fx-font-size: 17px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 13px 18px;"
                        + "-fx-background-radius: 10px;"
                        + "-fx-border-color: rgba(208, 174, 91, 0.65);"
                        + "-fx-border-radius: 10px;"
        );
        avisoTutorial.setLayoutX((LARGURA_JANELA - 680) / 2.0);
        avisoTutorial.setLayoutY(ALTURA_JANELA - 112);
        avisoTutorial.setVisible(tutorialAtivo[0]);
        avisoTutorial.setMouseTransparent(true);

        Label indicadorDepuracao = new Label(
                nomeTecla(AcaoControle.DEPURACAO)
                        + " — A* ATIVO  |  vermelho: bloqueado  "
                        + "verde: livre  linhas: caminhos"
        );
        indicadorDepuracao.setStyle(
                "-fx-background-color: rgba(8, 35, 55, 0.94);"
                        + "-fx-text-fill: #8fe8ff;"
                        + "-fx-font-size: 14px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 8px 12px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorDepuracao.setLayoutX(20);
        indicadorDepuracao.setLayoutY(ALTURA_JANELA - 48);
        indicadorDepuracao.setVisible(false);
        indicadorDepuracao.setMouseTransparent(true);

        areaVisivel.getChildren().addAll(
                avisoAlerta,
                indicadorSubchefe,
                indicadorVida,
                botaoMochila,
                indicadorInventario,
                avisoBau,
                avisoColeta,
                avisoTutorial,
                indicadorDepuracao,
                painelResultado
        );

        boolean[] jogoPausado = {false};
        boolean[] escapePausaPressionado = {false};
        AnimationTimer[] referenciaLoop = {null};
        Pane camadaPausa = new Pane();
        camadaPausa.setPrefSize(LARGURA_JANELA, ALTURA_JANELA);
        camadaPausa.setStyle("-fx-background-color: rgba(5, 6, 10, 0.78);");
        camadaPausa.setVisible(false);

        Label tituloPausa = new Label("JOGO PAUSADO");
        tituloPausa.setStyle(
                "-fx-text-fill: #f1d79a;"
                        + "-fx-font-size: 34px;"
                        + "-fx-font-weight: bold;"
        );
        Button continuarPausa = criarBotaoMenu("CONTINUAR");
        Button configuracoesPausa = criarBotaoMenu("CONFIGURAÇÕES");
        Button reiniciarPausa = criarBotaoMenu("REINICIAR FASE");
        Button menuPrincipalPausa = criarBotaoMenu("MENU PRINCIPAL");

        Label confirmarSaida = new Label(
                "Abandonar a fase e voltar ao menu?"
        );
        confirmarSaida.setStyle(
                "-fx-text-fill: #ffd2c2; -fx-font-size: 15px;"
        );
        Button confirmarMenu = criarBotaoMenu("SIM, VOLTAR");
        Button cancelarMenu = criarBotaoMenu("CANCELAR");
        confirmarMenu.setPrefWidth(145);
        cancelarMenu.setPrefWidth(145);
        HBox botoesConfirmacao = new HBox(
                10, confirmarMenu, cancelarMenu
        );
        botoesConfirmacao.setAlignment(Pos.CENTER);
        VBox confirmacaoSaida = new VBox(
                8, confirmarSaida, botoesConfirmacao
        );
        confirmacaoSaida.setAlignment(Pos.CENTER);
        confirmacaoSaida.setVisible(false);
        confirmacaoSaida.setManaged(false);

        VBox caixaPausa = new VBox(
                10,
                tituloPausa,
                continuarPausa,
                configuracoesPausa,
                reiniciarPausa,
                menuPrincipalPausa,
                confirmacaoSaida
        );
        caixaPausa.setAlignment(Pos.CENTER);
        caixaPausa.setLayoutX((LARGURA_JANELA - 420) / 2.0);
        caixaPausa.setLayoutY(76);
        caixaPausa.setPrefWidth(420);
        caixaPausa.setPadding(new Insets(26));
        caixaPausa.setStyle(
                "-fx-background-color: rgba(25, 18, 20, 0.97);"
                        + "-fx-border-color: #b9854d;"
                        + "-fx-border-radius: 10px;"
                        + "-fx-background-radius: 10px;"
        );
        camadaPausa.getChildren().add(caixaPausa);
        areaVisivel.getChildren().add(camadaPausa);

        // Cria uma janela menor que o mapa para permitir o uso da câmera.
        Scene cena = new Scene(areaVisivel, LARGURA_JANELA, ALTURA_JANELA);
        boolean[] transicaoPortaAtiva = {false};

        continuarPausa.setOnAction(evento -> {
            jogoPausado[0] = false;
            camadaPausa.setVisible(false);
            confirmacaoSaida.setVisible(false);
            confirmacaoSaida.setManaged(false);
            teclasPressionadas.clear();
        });
        configuracoesPausa.setOnAction(evento ->
                mostrarTelaConfiguracoes(janela, () -> {
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                    avisoBau.setText(
                            "Pressione "
                                    + nomeTecla(AcaoControle.INTERAGIR)
                                    + " para abrir o baú"
                    );
                    janela.setScene(cena);
                })
        );
        reiniciarPausa.setOnAction(evento -> {
            if (referenciaLoop[0] != null) {
                referenciaLoop[0].stop();
            }
            teclasPressionadas.clear();
            if (jogadorDerrotado[0]) {
                limparRetornoSalaLateral();
                iniciarFase(
                        janela,
                        "entrada_labirinto.tmx",
                        "saida_escadaria",
                        false,
                        100
                );
            } else {
                iniciarFase(
                        janela,
                        nomeMapa,
                        nomePontoSaida,
                        armazem,
                        100
                );
            }
        });
        menuPrincipalPausa.setOnAction(evento -> {
            confirmacaoSaida.setVisible(true);
            confirmacaoSaida.setManaged(true);
        });
        cancelarMenu.setOnAction(evento -> {
            confirmacaoSaida.setVisible(false);
            confirmacaoSaida.setManaged(false);
        });
        confirmarMenu.setOnAction(evento -> {
            if (referenciaLoop[0] != null) {
                referenciaLoop[0].stop();
            }
            teclasPressionadas.clear();
            mostrarTelaInicial(janela);
        });

        // Define uma cor escura temporária para o fundo.
        areaVisivel.setStyle("-fx-background-color: #20252b;");

        /*
         * Quando uma tecla for pressionada,
         * ela será adicionada ao conjunto.
         */
        cena.setOnKeyPressed(evento -> {
            boolean primeiraPressao =
                    teclasPressionadas.add(evento.getCode());

            if (evento.getCode() == KeyCode.ESCAPE) {
                if (!escapePausaPressionado[0]) {
                    escapePausaPressionado[0] = true;
                    jogoPausado[0] = !jogoPausado[0];
                    camadaPausa.setVisible(jogoPausado[0]);
                    confirmacaoSaida.setVisible(false);
                    confirmacaoSaida.setManaged(false);
                    teclasPressionadas.clear();
                }
                evento.consume();
                return;
            }

            if (evento.getCode() == tecla(AcaoControle.DEPURACAO)
                    && primeiraPressao) {
                boolean ativar = !depuracaoNavegacao.isVisible();
                depuracaoNavegacao.setVisible(ativar);
                indicadorDepuracao.setVisible(ativar);

                if (ativar) {
                    atualizarDepuracaoNavegacao(
                            depuracaoNavegacao,
                            carregadorMapa,
                            guardas,
                            ultimaPosicaoVista[0],
                            alertaAtivo[0]
                    );
                }
            }
        });

        /*
         * Quando a tecla for solta,
         * ela será removida do conjunto.
         */
        cena.setOnKeyReleased(evento -> {
            teclasPressionadas.remove(evento.getCode());
            if (evento.getCode() == KeyCode.ESCAPE) {
                escapePausaPressionado[0] = false;
            }
        });

        Runnable derrubarItensDoJogador = () -> {
            ItensPerdidos conteudo = new ItensPerdidos(
                    nomeMapa,
                    centroDoJogador(jogador),
                    shurikens[0],
                    bombasFumaca[0],
                    kitsMedicos[0],
                    inventarioKatana
            );

            if (!conteudo.vazio()) {
                itensPerdidos.add(conteudo);
                BolsaItensPerdidos bolsa =
                        new BolsaItensPerdidos(conteudo);
                bolsasItensPerdidos.add(bolsa);
                mundo.getChildren().add(bolsa);
            }

            shurikens[0] = 0;
            bombasFumaca[0] = 0;
            kitsMedicos[0] = 0;
            inventarioKatana = false;
            salvarInventario(0, 0, 0);
            indicadorInventario.atualizar(0, 0, 0, false);
        };

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

            // O painel F3 não precisa redesenhar centenas de tiles a 60 FPS.
            private double tempoParaAtualizarDepuracao = 0;

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

                // Enquanto a porta fecha, congela a fase para impedir
                // movimento, ataques ou uma segunda troca de sala.
                if (transicaoPortaAtiva[0]) {
                    return;
                }

                // A tela continua desenhada ao fundo, mas nenhum personagem,
                // projétil, tutorial ou inimigo avança durante a pausa.
                if (jogoPausado[0]) {
                    return;
                }

                /*
                 * Depois da vitória ou derrota, R recria a fase inteira.
                 * O loop antigo é encerrado antes de montar a nova cena.
                 */
                if ((faseConcluida[0] || jogadorDerrotado[0])
                        && teclasPressionadas.contains(
                                tecla(AcaoControle.REINICIAR)
                        )) {
                    stop();
                    teclasPressionadas.clear();
                    if (jogadorDerrotado[0]) {
                        limparRetornoSalaLateral();
                        iniciarFase(
                                janela,
                                "entrada_labirinto.tmx",
                                "saida_escadaria",
                                false,
                                100
                        );
                    } else {
                        iniciarFase(
                                janela,
                                nomeMapa,
                                nomePontoSaida,
                                armazem,
                                100
                        );
                    }
                    return;
                }

                // Controla o tempo da mensagem exibida depois de abrir um baú.
                if (tempoMensagemColeta[0] > 0) {
                    tempoMensagemColeta[0] -= tempoDecorrido;
                    avisoColeta.setVisible(true);
                } else {
                    avisoColeta.setVisible(false);
                }

                tempoAteProximoPasso[0] -= tempoDecorrido;
                tempoProtecaoAtaqueCorpoACorpo[0] = Math.max(
                        0,
                        tempoProtecaoAtaqueCorpoACorpo[0] - tempoDecorrido
                );
                ruidoDeItemNesteQuadro[0] = false;
                tempoPassoAudivel[0] = Math.max(
                        0,
                        tempoPassoAudivel[0] - tempoDecorrido
                );
                if (tempoIndicadorRuido[0] > 0) {
                    tempoIndicadorRuido[0] -= tempoDecorrido;
                    double progresso = 1.0 - Math.max(
                            0,
                            tempoIndicadorRuido[0] / 0.32
                    );
                    indicadorRuidoPasso.setRadius(
                            8 + progresso * raioVisualUltimoRuido[0]
                    );
                    indicadorRuidoPasso.setOpacity(1.0 - progresso);
                    indicadorRuidoPasso.setVisible(true);
                } else {
                    indicadorRuidoPasso.setVisible(false);
                }

                boolean teclaEPressionada =
                        teclasPressionadas.contains(
                                tecla(AcaoControle.INTERAGIR)
                        );

                if (!teclaEPressionada) {
                    teclaEConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(
                        tecla(AcaoControle.KIT_MEDICO)
                )) {
                    teclaHConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(
                        tecla(AcaoControle.FUMACA)
                )) {
                    teclaQConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(
                        tecla(AcaoControle.SHURIKEN)
                )) {
                    teclaEspacoConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(
                        tecla(AcaoControle.KATANA)
                )) {
                    teclaFConsumida[0] = false;
                }

                if (tutorialAtivo[0]) {
                    boolean pulouTutorial =
                            teclasPressionadas.contains(
                                    tecla(AcaoControle.PULAR_TUTORIAL)
                            );
                    boolean tentandoMover =
                            teclasPressionadas.contains(
                                    tecla(AcaoControle.CIMA)
                            ) || teclasPressionadas.contains(
                                    tecla(AcaoControle.ESQUERDA)
                            ) || teclasPressionadas.contains(
                                    tecla(AcaoControle.BAIXO)
                            ) || teclasPressionadas.contains(
                                    tecla(AcaoControle.DIREITA)
                            );

                    if (pulouTutorial) {
                        tutorialAtivo[0] = false;
                        avisoTutorial.setVisible(false);
                        if (tutorialDeItens) {
                            marcarTutorialItensConcluido();
                        } else {
                            marcarTutorialConcluido();
                        }
                    } else if (tutorialDeItens) {
                        tempoEtapaTutorial[0] += tempoDecorrido;
                        int totalItensAtual = shurikens[0]
                                + bombasFumaca[0] + kitsMedicos[0];

                        if (etapaTutorial[0] == 0
                                && (totalItensAtual
                                > totalItensInicioTutorial
                                || tempoEtapaTutorial[0] >= 10.0)) {
                            etapaTutorial[0] = 1;
                            tempoEtapaTutorial[0] = 0;
                            avisoTutorial.setText(
                                    textoEtapaTutorialItens(1)
                            );
                        } else if (etapaTutorial[0] == 1
                                && tempoEtapaTutorial[0] >= 4.5) {
                            etapaTutorial[0] = 2;
                            tempoEtapaTutorial[0] = 0;
                            avisoTutorial.setText(
                                    textoEtapaTutorialItens(2)
                            );
                        } else if (etapaTutorial[0] == 2
                                && tempoEtapaTutorial[0] >= 4.5) {
                            etapaTutorial[0] = 3;
                            tempoEtapaTutorial[0] = 0;
                            avisoTutorial.setText(
                                    textoEtapaTutorialItens(3)
                            );
                        } else if (etapaTutorial[0] == 3
                                && tempoEtapaTutorial[0] >= 5.5) {
                            tutorialAtivo[0] = false;
                            avisoTutorial.setVisible(false);
                            marcarTutorialItensConcluido();
                        }
                    } else if (etapaTutorial[0] == 0
                            && centroDoJogador(jogador).distance(
                                    pontoInicioTutorial[0]
                            ) >= 28) {
                        etapaTutorial[0] = 1;
                        tempoEtapaTutorial[0] = 0;
                        avisoTutorial.setText(textoEtapaTutorial(1));
                    } else if (etapaTutorial[0] == 1
                            && tentandoMover
                            && teclasPressionadas.contains(
                                    tecla(AcaoControle.FURTIVO)
                            )) {
                        etapaTutorial[0] = 2;
                        tempoEtapaTutorial[0] = 0;
                        avisoTutorial.setText(textoEtapaTutorial(2));
                    } else if (etapaTutorial[0] == 2) {
                        tempoEtapaTutorial[0] += tempoDecorrido;

                        if (guardaInvestigando[0] != null
                                || tempoEtapaTutorial[0] >= 9.0) {
                            etapaTutorial[0] = 3;
                            tempoEtapaTutorial[0] = 0;
                            avisoTutorial.setText(textoEtapaTutorial(3));
                        }
                    } else if (etapaTutorial[0] == 3) {
                        tempoEtapaTutorial[0] += tempoDecorrido;

                        if (tempoEtapaTutorial[0] >= 6.0) {
                            tutorialAtivo[0] = false;
                            avisoTutorial.setVisible(false);
                            marcarTutorialConcluido();
                        }
                    }
                }

                // Congela guardas, disparos e jogador enquanto o resultado aparece.
                if (faseConcluida[0] || jogadorDerrotado[0]) {
                    if (jogadorDerrotado[0]) {
                        visualJogador.setRotate(90);
                        visualJogador.setOpacity(0.62);
                    }
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

                if (teclasPressionadas.contains(
                        tecla(AcaoControle.KIT_MEDICO)
                )
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
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                    avisoColeta.setText("Kit médico usado: +30 de vida");
                    tempoMensagemColeta[0] = 2.5;
                }

                if (teclasPressionadas.contains(
                        tecla(AcaoControle.FUMACA)
                )
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

                    // O impacto da bomba encobre a visão, mas também chama
                    // mais atenção que um passo ou uma shuriken na parede.
                    somPassos.tocarImpactoFumaca();
                    ruidoDeItemNesteQuadro[0] = true;
                    posicaoUltimoPasso[0] = centroFumaca;
                    alcanceUltimoPasso[0] = ALCANCE_RUIDO_FUMACA;
                    tempoPassoAudivel[0] = 0.28;
                    passoJaInvestigado[0] = false;
                    raioVisualUltimoRuido[0] = 68;
                    indicadorRuidoPasso.setCenterX(centroFumaca.getX());
                    indicadorRuidoPasso.setCenterY(centroFumaca.getY());
                    indicadorRuidoPasso.setStroke(
                            Color.rgb(185, 190, 205, 0.78)
                    );
                    indicadorRuidoPasso.setRadius(8);
                    indicadorRuidoPasso.setOpacity(1);
                    indicadorRuidoPasso.setVisible(true);
                    tempoIndicadorRuido[0] = 0.32;

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                    avisoColeta.setText(
                            "Bomba de fumaça usada: visão bloqueada por 5s"
                    );
                    tempoMensagemColeta[0] = 2.5;
                }


                if (teclasPressionadas.contains(
                        tecla(AcaoControle.SHURIKEN)
                )
                        && !teclaEspacoConsumida[0]
                        && shurikens[0] > 0) {
                    teclaEspacoConsumida[0] = true;
                    shurikens[0]--;
                    iniciarAcaoVisualJogador(
                            2,
                            acaoVisualJogador,
                            quadroVisualJogador,
                            tempoQuadroJogador,
                            tempoAcaoJogador
                    );

                    ShurikenJogador shuriken = new ShurikenJogador(
                            centroDoJogador(jogador),
                            ultimaDirecaoJogador[0]
                    );
                    shurikensLancadas.add(shuriken);
                    mundo.getChildren().add(shuriken);

                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                }

                // Na arena, F só usa a katana depois que ela for coletada.
                if (arenaSubchefe
                        && subchefe != null
                        && !subchefe.estaDerrotado()
                        && teclasPressionadas.contains(
                                tecla(AcaoControle.KATANA)
                        )
                        && !teclaFConsumida[0]) {
                    teclaFConsumida[0] = true;

                    if (!inventarioKatana) {
                        avisoColeta.setText(
                                "A katana cairá na segunda investida!"
                        );
                        tempoMensagemColeta[0] = 1.5;
                    } else {
                        iniciarAcaoVisualJogador(
                                1,
                                acaoVisualJogador,
                                quadroVisualJogador,
                                tempoQuadroJogador,
                                tempoAcaoJogador
                        );
                    }

                    if (inventarioKatana
                            && subchefe.receberGolpe(jogador)) {
                        indicadorSubchefe.setText(
                                "Subchefe — Vida: "
                                        + subchefe.obterVida() + "/"
                                        + subchefe.obterVidaMaxima()
                                        + "  |  " + textoControleKatana()
                        );
                        avisoColeta.setText("Golpe acertado!");
                        tempoMensagemColeta[0] = 0.7;

                        if (subchefe.estaDerrotado()) {
                            salvarCheckpointSubchefe(
                                    shurikens[0],
                                    bombasFumaca[0],
                                    kitsMedicos[0]
                            );
                            mundo.getChildren().remove(subchefe);
                            mundo.getChildren().removeAll(
                                    projeteisSubchefe
                            );
                            projeteisSubchefe.clear();
                            faseConcluida[0] = true;
                            stop();
                            mostrarTelaCheckpointSubchefe(janela);
                            return;
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
                            guarda.definirTipoVisual(
                                    GuardaPatrulha.TipoVisual.REFORCO
                            );
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
                        tempoDanoVisualJogador[0] = 0.28;
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            derrubarItensDoJogador.run();
                            jogadorDerrotado[0] = true;
                            avisoAlerta.setVisible(false);
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "O subchefe derrotou você.\n"
                                            + "Seus itens ficaram neste local.\n\n"
                                            + "Pressione "
                                            + nomeTecla(AcaoControle.REINICIAR)
                                            + " para voltar ao início"
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
                        tempoDanoVisualJogador[0] = 0.28;
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            derrubarItensDoJogador.run();
                            jogadorDerrotado[0] = true;
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "O subchefe derrotou você.\n"
                                            + "Seus itens ficaram neste local.\n\n"
                                            + "Pressione "
                                            + nomeTecla(AcaoControle.REINICIAR)
                                            + " para voltar ao início"
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
                    item.atualizar(tempoDecorrido);

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
                                "Katana coletada! Pressione "
                                        + nomeTecla(AcaoControle.KATANA)
                                        + " para atacar."
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
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                    tempoMensagemColeta[0] = 1.5;
                    mundo.getChildren().remove(item);
                    iteradorItens.remove();
                }

                Iterator<BolsaItensPerdidos> iteradorBolsas =
                        bolsasItensPerdidos.iterator();
                while (iteradorBolsas.hasNext()) {
                    BolsaItensPerdidos bolsa = iteradorBolsas.next();
                    bolsa.atualizar(tempoDecorrido);

                    if (!bolsa.tocou(jogador)) {
                        continue;
                    }

                    ItensPerdidos recuperados = bolsa.conteudo;
                    shurikens[0] += recuperados.shurikens();
                    bombasFumaca[0] += recuperados.bombasFumaca();
                    kitsMedicos[0] += recuperados.kitsMedicos();
                    inventarioKatana = inventarioKatana
                            || recuperados.katana();
                    salvarInventario(
                            shurikens[0], bombasFumaca[0], kitsMedicos[0]
                    );
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
                    avisoColeta.setText(
                            "Itens da derrota anterior recuperados!"
                    );
                    tempoMensagemColeta[0] = 2.5;
                    itensPerdidos.remove(recuperados);
                    mundo.getChildren().remove(bolsa);
                    iteradorBolsas.remove();
                }

                if (!alertaAtivo[0]) {
                    boolean investigadorChegouAoLocal = false;

                    // Cada guarda mantém seu próprio estado fora do alerta.
                    for (GuardaPatrulha guarda : guardas) {
                        if (guarda.estaRetornandoPatrulha()) {
                            guarda.atualizarRetornoPatrulha(tempoDecorrido);
                        } else if (guarda.estaInvestigando()) {
                            investigadorChegouAoLocal =
                                    guarda.atualizarInvestigacao(
                                            tempoDecorrido
                                    );
                        } else {
                            guarda.atualizarPatrulha(tempoDecorrido);
                        }
                    }

                    if (guardaInvestigando[0] != null
                            && !guardas.contains(guardaInvestigando[0])) {
                        guardaInvestigando[0] = null;
                        tempoInvestigandoLocal[0] = 0;
                    }

                    if (guardaInvestigando[0] != null
                            && investigadorChegouAoLocal) {
                        tempoInvestigandoLocal[0] += tempoDecorrido;
                        avisoAlerta.setText(
                                "Um guarda está investigando o barulho..."
                        );

                        if (tempoInvestigandoLocal[0] >= 2.0) {
                            GuardaPatrulha investigador =
                                    guardaInvestigando[0];
                            Point2D pontoDeRetorno =
                                    investigador.obterPontoPatrulhaMaisProximo();
                            investigador.iniciarRetornoPatrulha(
                                    carregadorMapa.encontrarCaminho(
                                            investigador.obterPosicao(),
                                            pontoDeRetorno
                                    ),
                                    pontoDeRetorno
                            );
                            guardaInvestigando[0] = null;
                            tempoInvestigandoLocal[0] = 0;
                            avisoAlerta.setVisible(false);
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
                        guardaInvestigando[0] = null;
                        tempoInvestigandoLocal[0] = 0;
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
                    } else if (tempoPassoAudivel[0] > 0
                            && !passoJaInvestigado[0]) {
                        GuardaPatrulha ouvinte =
                                encontrarGuardaQueOuvePasso(
                                    guardas,
                                    posicaoUltimoPasso[0],
                                    alcanceUltimoPasso[0]
                                );

                        if (ouvinte != null) {
                            passoJaInvestigado[0] = true;

                            if (guardaInvestigando[0] != null
                                    && guardaInvestigando[0] != ouvinte) {
                                GuardaPatrulha anterior =
                                        guardaInvestigando[0];
                                Point2D retornoAnterior = anterior
                                        .obterPontoPatrulhaMaisProximo();
                                anterior.iniciarRetornoPatrulha(
                                        carregadorMapa.encontrarCaminho(
                                                anterior.obterPosicao(),
                                                retornoAnterior
                                        ),
                                        retornoAnterior
                                );
                            }

                            guardaInvestigando[0] = ouvinte;
                            tempoInvestigandoLocal[0] = 0;
                            ouvinte.iniciarInvestigacao(
                                    carregadorMapa.encontrarCaminho(
                                            ouvinte.obterPosicao(),
                                            posicaoUltimoPasso[0]
                                    )
                            );
                            avisoAlerta.setText(
                                    "Um guarda ouviu algo..."
                            );
                            avisoAlerta.setVisible(true);
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
                    boolean jogadorAudivelAgora = tempoPassoAudivel[0] > 0
                            && algumGuardaOuvePasso(
                                    guardas,
                                    posicaoUltimoPasso[0],
                                    alcanceUltimoPasso[0]
                            );

                    if (jogadorVisivelAgora || jogadorAudivelAgora) {
                        tempoSemVerJogador[0] = 0;
                        ultimaPosicaoVista[0] = jogadorVisivelAgora
                                ? centroDoJogador(jogador)
                                : posicaoUltimoPasso[0];
                        avisoAlerta.setText(jogadorVisivelAgora
                                ? "ALERTA: os guardas viram você!"
                                : "Os guardas ouviram seus passos...");
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
                            for (GuardaPatrulha guarda : guardas) {
                                /*
                                 * Cada guarda persegue o jogador pela direção
                                 * em que já está chegando. Antes, destinos
                                 * distribuídos em um círculo faziam alguns
                                 * correrem para cantos que pareciam aleatórios.
                                 * A pequena folga evita apenas que todos se
                                 * empilhem exatamente no mesmo ponto.
                                 */
                                Point2D vetorDeAproximacao =
                                        guarda.obterPosicao().subtract(
                                                ultimaPosicaoVista[0]
                                        );
                                Point2D destino = ultimaPosicaoVista[0];
                                double distanciaDesejada = guarda
                                        .obterDistanciaCombatePreferida();

                                if (vetorDeAproximacao.magnitude() > 0.001) {
                                    destino = destino.add(
                                            vetorDeAproximacao.normalize()
                                                    .multiply(distanciaDesejada)
                                    );
                                } else if (guarda.ehGuardaDistancia()) {
                                    destino = destino.add(distanciaDesejada, 0);
                                }

                                guarda.definirCaminhoPerseguicao(
                                        carregadorMapa.encontrarCaminho(
                                                guarda.obterPosicao(),
                                                destino
                                        )
                                );
                            }

                            // Evita trocar a rota antes de o guarda completar
                            // ao menos parte do próximo trecho calculado.
                            tempoParaRecalcularPerseguicao = 0.55;
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

                            if (!jogadorDerrotado[0]
                                    && !jogadorProtegidoPelaFumaca
                                    && tempoProtecaoAtaqueCorpoACorpo[0] <= 0
                                    && guarda.podeAtacarCorpoACorpo(
                                            jogador,
                                            tempoDecorrido
                                    )) {
                                vidaJogador[0] = Math.max(
                                        0,
                                        vidaJogador[0]
                                                - guarda.obterDanoAtaqueCorpoACorpo()
                                );
                                tempoProtecaoAtaqueCorpoACorpo[0] = 0.48;
                                tempoDanoVisualJogador[0] = 0.28;
                                indicadorVida.setText(
                                        "Vida: " + vidaJogador[0]
                                );

                                if (vidaJogador[0] == 0) {
                                    derrubarItensDoJogador.run();
                                    jogadorDerrotado[0] = true;
                                    avisoAlerta.setVisible(false);
                                    avisoSaida.setVisible(false);
                                    painelResultado.setText(
                                            "Você foi derrotado.\n"
                                                    + "Seus itens ficaram neste local.\n\n"
                                                    + "Pressione "
                                                    + nomeTecla(AcaoControle.REINICIAR)
                                                    + " para voltar ao início"
                                    );
                                    painelResultado.setVisible(true);
                                    teclasPressionadas.clear();
                                }
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

                if (depuracaoNavegacao.isVisible()) {
                    tempoParaAtualizarDepuracao -= tempoDecorrido;

                    if (tempoParaAtualizarDepuracao <= 0) {
                        atualizarDepuracaoNavegacao(
                                depuracaoNavegacao,
                                carregadorMapa,
                                guardas,
                                ultimaPosicaoVista[0],
                                alertaAtivo[0]
                        );
                        tempoParaAtualizarDepuracao = 0.10;
                    }
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
                        tempoDanoVisualJogador[0] = 0.28;
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            derrubarItensDoJogador.run();
                            jogadorDerrotado[0] = true;
                            avisoAlerta.setVisible(false);
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "Você foi derrotado.\n"
                                            + "Seus itens ficaram neste local.\n\n"
                                            + "Pressione "
                                            + nomeTecla(AcaoControle.REINICIAR)
                                            + " para voltar ao início"
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
                                iteradorGuardas.remove();
                                guarda.reproduzirDerrota(
                                        () -> mundo.getChildren().remove(
                                                guarda
                                        )
                                );
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
                            salvarCheckpointSubchefe(
                                    shurikens[0],
                                    bombasFumaca[0],
                                    kitsMedicos[0]
                            );
                            mundo.getChildren().remove(subchefe);
                            mundo.getChildren().removeAll(
                                    projeteisSubchefe
                            );
                            projeteisSubchefe.clear();
                            faseConcluida[0] = true;
                            stop();
                            mostrarTelaCheckpointSubchefe(janela);
                            return;
                        }
                    }

                    boolean atingiuObstaculo = !atingiuGuarda
                            && !atingiuSubchefe
                            && shuriken.atingiuObstaculo(carregadorMapa);

                    if (atingiuObstaculo) {
                        Point2D pontoImpacto = shuriken.obterPosicao();
                        somPassos.tocarImpactoShuriken();
                        ruidoDeItemNesteQuadro[0] = true;
                        posicaoUltimoPasso[0] = pontoImpacto;
                        alcanceUltimoPasso[0] = ALCANCE_RUIDO_SHURIKEN;
                        tempoPassoAudivel[0] = 0.22;
                        passoJaInvestigado[0] = false;
                        raioVisualUltimoRuido[0] = 52;
                        indicadorRuidoPasso.setCenterX(pontoImpacto.getX());
                        indicadorRuidoPasso.setCenterY(pontoImpacto.getY());
                        indicadorRuidoPasso.setStroke(
                                Color.rgb(160, 205, 235, 0.78)
                        );
                        indicadorRuidoPasso.setRadius(8);
                        indicadorRuidoPasso.setOpacity(1);
                        indicadorRuidoPasso.setVisible(true);
                        tempoIndicadorRuido[0] = 0.32;
                    }

                    if (atingiuGuarda
                            || atingiuSubchefe
                            || atingiuObstaculo
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
                if (teclasPressionadas.contains(
                        tecla(AcaoControle.CIMA)
                )) {
                    direcaoY -= 1;
                }

                // S movimenta o jogador para baixo.
                if (teclasPressionadas.contains(
                        tecla(AcaoControle.BAIXO)
                )) {
                    direcaoY += 1;
                }

                // A movimenta o jogador para a esquerda.
                if (teclasPressionadas.contains(
                        tecla(AcaoControle.ESQUERDA)
                )) {
                    direcaoX -= 1;
                }

                // D movimenta o jogador para a direita.
                if (teclasPressionadas.contains(
                        tecla(AcaoControle.DIREITA)
                )) {
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
                double posicaoAnteriorJogadorX = jogador.getX();
                double posicaoAnteriorJogadorY = jogador.getY();
                boolean movimentoFurtivo =
                        teclasPressionadas.contains(
                                tecla(AcaoControle.FURTIVO)
                        );
                double velocidadeAtual = movimentoFurtivo
                        ? VELOCIDADE_FURTIVA
                        : VELOCIDADE_JOGADOR;
                double novaPosicaoX = jogador.getX()
                        + direcaoX * velocidadeAtual * tempoDecorrido;

                double novaPosicaoY = jogador.getY()
                        + direcaoY * velocidadeAtual * tempoDecorrido;

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

                double distanciaPercorrida = Math.hypot(
                        jogador.getX() - posicaoAnteriorJogadorX,
                        jogador.getY() - posicaoAnteriorJogadorY
                );
                if (distanciaPercorrida > 0.05
                        && !ruidoDeItemNesteQuadro[0]
                        && tempoAteProximoPasso[0] <= 0) {
                    somPassos.tocar(movimentoFurtivo);
                    tempoAteProximoPasso[0] = movimentoFurtivo
                            ? INTERVALO_PASSOS_FURTIVOS
                            : INTERVALO_PASSOS;
                    tempoPassoAudivel[0] = movimentoFurtivo ? 0.10 : 0.18;
                    alcanceUltimoPasso[0] = movimentoFurtivo
                            ? ALCANCE_AUDICAO_FURTIVO
                            : ALCANCE_AUDICAO_PASSOS;
                    posicaoUltimoPasso[0] = centroDoJogador(jogador);
                    passoJaInvestigado[0] = false;
                    indicadorRuidoPasso.setCenterX(
                            posicaoUltimoPasso[0].getX()
                    );
                    indicadorRuidoPasso.setCenterY(
                            posicaoUltimoPasso[0].getY()
                    );
                    indicadorRuidoPasso.setStroke(movimentoFurtivo
                            ? Color.rgb(120, 205, 165, 0.65)
                            : Color.rgb(245, 205, 110, 0.75));
                    raioVisualUltimoRuido[0] = movimentoFurtivo ? 22 : 44;
                    indicadorRuidoPasso.setRadius(8);
                    indicadorRuidoPasso.setOpacity(1);
                    indicadorRuidoPasso.setVisible(true);
                    tempoIndicadorRuido[0] = 0.32;
                }

                atualizarAnimacaoJogador(
                        visualJogador,
                        spriteCaminhadaJogador,
                        spriteKatanaJogador,
                        spriteShurikenJogador,
                        ultimaDirecaoJogador[0],
                        direcaoX != 0 || direcaoY != 0,
                        jogadorDerrotado[0],
                        tempoDecorrido,
                        acaoVisualJogador,
                        quadroVisualJogador,
                        tempoQuadroJogador,
                        tempoAcaoJogador,
                        tempoDanoVisualJogador
                );

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
                    indicadorInventario.atualizar(
                            shurikens[0], bombasFumaca[0],
                            kitsMedicos[0], inventarioKatana
                    );
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
                        ? "Saída liberada — pressione "
                        + nomeTecla(AcaoControle.INTERAGIR) + " para continuar"
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
                            "Entrar nesta sala — pressione "
                                    + nomeTecla(AcaoControle.INTERAGIR)
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
                        trocarFasePelaPorta(
                                areaVisivel,
                                transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            portaProxima.mapaDestino(),
                                            "saida_sala_lateral",
                                            false,
                                            vidaJogador[0]
                                    );
                                }
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
                        trocarFasePelaPorta(
                                areaVisivel,
                                transicaoPortaAtiva,
                                () -> {
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
                                }
                        );
                        return;
                    }

                    if ("entrada_labirinto.tmx".equals(nomeMapa)) {
                        trocarFasePelaPorta(
                                areaVisivel, transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            "armazem_dojo.tmx",
                                            "saida_armazem",
                                            true,
                                            vidaJogador[0]
                                    );
                                }
                        );
                        return;
                    }

                    if ("armazem_dojo.tmx".equals(nomeMapa)) {
                        trocarFasePelaPorta(
                                areaVisivel, transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            "dojo_guardas.tmx",
                                            "saida_dojo",
                                            false,
                                            vidaJogador[0]
                                    );
                                }
                        );
                        return;
                    }

                    if ("dojo_guardas.tmx".equals(nomeMapa)) {
                        trocarFasePelaPorta(
                                areaVisivel, transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            "sala_treinamento.tmx",
                                            "saida_treinamento",
                                            false,
                                            vidaJogador[0]
                                    );
                                }
                        );
                        return;
                    }

                    if ("sala_treinamento.tmx".equals(nomeMapa)) {
                        trocarFasePelaPorta(
                                areaVisivel, transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            "patio_noturno.tmx",
                                            "saida_patio",
                                            false,
                                            vidaJogador[0]
                                    );
                                }
                        );
                        return;
                    }

                    if ("patio_noturno.tmx".equals(nomeMapa)) {
                        trocarFasePelaPorta(
                                areaVisivel, transicaoPortaAtiva,
                                () -> {
                                    stop();
                                    teclasPressionadas.clear();
                                    iniciarFase(
                                            janela,
                                            "arena_subchefe.tmx",
                                            "saida_arena",
                                            false,
                                            vidaJogador[0]
                                    );
                                }
                        );
                        return;
                    }

                    transicaoPortaAtiva[0] = true;
                    teclasPressionadas.clear();
                    salvarCheckpointSubchefe(
                            shurikens[0],
                            bombasFumaca[0],
                            kitsMedicos[0]
                    );
                    animarFechamentoPorta(areaVisivel, () -> {
                        faseConcluida[0] = true;
                        stop();
                        mostrarTelaCheckpointSubchefe(janela);
                    });
                }
            }
        };

        // Guarda a referência para que os botões da pausa possam encerrar
        // este ciclo antes de reiniciar a fase ou voltar ao menu.
        referenciaLoop[0] = loopDoJogo;

        // Inicia o ciclo de atualização do jogo.
        loopDoJogo.start();

        // Configura e exibe a janela.
        janela.setTitle("Hangetsu: Shadow Dojo");
        janela.setScene(cena);

        // Mantém a janela no tamanho exato do mapa durante este primeiro teste.
        janela.setResizable(false);

        janela.show();

        if (abrirPortaAoCarregar) {
            abrirPortaAoCarregar = false;
            animarAberturaPorta(areaVisivel);
        }

        // Posiciona a câmera corretamente antes do primeiro movimento.
        atualizarCamera(mundo, jogador, cena, visualMapa);

        // Confere também a saída antes do primeiro quadro do jogo.
        atualizarAvisoSaida(jogador, pontoSaida, avisoSaida);
    }

    /** Fecha as folhas shoji e só então carrega a próxima sala. */
    private void trocarFasePelaPorta(
            Pane areaVisivel,
            boolean[] transicaoAtiva,
            Runnable carregarProximaFase
    ) {
        if (transicaoAtiva[0]) {
            return;
        }

        transicaoAtiva[0] = true;
        teclasPressionadas.clear();
        animarFechamentoPorta(areaVisivel, () -> {
            abrirPortaAoCarregar = true;
            carregarProximaFase.run();
        });
    }

    private void animarFechamentoPorta(
            Pane areaVisivel,
            Runnable aoFechar
    ) {
        Pane camada = criarCamadaPortaShoji();
        Pane esquerda = (Pane) camada.getChildren().get(1);
        Pane direita = (Pane) camada.getChildren().get(2);
        double meiaLargura = LARGURA_JANELA / 2.0;

        esquerda.setTranslateX(-meiaLargura);
        direita.setTranslateX(meiaLargura);
        areaVisivel.getChildren().add(camada);

        TranslateTransition fecharEsquerda = new TranslateTransition(
                Duration.millis(380), esquerda
        );
        fecharEsquerda.setToX(0);
        fecharEsquerda.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition fecharDireita = new TranslateTransition(
                Duration.millis(380), direita
        );
        fecharDireita.setToX(0);
        fecharDireita.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition fechamento = new ParallelTransition(
                fecharEsquerda, fecharDireita
        );
        fechamento.setOnFinished(evento -> {
            aoFechar.run();
            areaVisivel.getChildren().remove(camada);
        });
        fechamento.play();
    }

    /** Abre as folhas depois que a sala de destino já está pronta. */
    private void animarAberturaPorta(Pane areaVisivel) {
        Pane camada = criarCamadaPortaShoji();
        Pane esquerda = (Pane) camada.getChildren().get(1);
        Pane direita = (Pane) camada.getChildren().get(2);
        double meiaLargura = LARGURA_JANELA / 2.0;
        areaVisivel.getChildren().add(camada);

        TranslateTransition abrirEsquerda = new TranslateTransition(
                Duration.millis(430), esquerda
        );
        abrirEsquerda.setToX(-meiaLargura);
        abrirEsquerda.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition abrirDireita = new TranslateTransition(
                Duration.millis(430), direita
        );
        abrirDireita.setToX(meiaLargura);
        abrirDireita.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition abertura = new ParallelTransition(
                abrirEsquerda, abrirDireita
        );
        abertura.setDelay(Duration.millis(90));
        abertura.setOnFinished(evento ->
                areaVisivel.getChildren().remove(camada)
        );
        abertura.play();
    }

    /** Monta duas folhas shoji em tela cheia para a transição. */
    private Pane criarCamadaPortaShoji() {
        Pane camada = new Pane();
        camada.setPrefSize(LARGURA_JANELA, ALTURA_JANELA);
        camada.setMinSize(LARGURA_JANELA, ALTURA_JANELA);
        camada.setMaxSize(LARGURA_JANELA, ALTURA_JANELA);
        camada.setMouseTransparent(false);

        Rectangle fundo = new Rectangle(
                LARGURA_JANELA, ALTURA_JANELA, Color.rgb(8, 5, 4, 0.72)
        );
        Pane esquerda = criarFolhaShoji(false);
        Pane direita = criarFolhaShoji(true);
        direita.setLayoutX(LARGURA_JANELA / 2.0);

        camada.getChildren().addAll(fundo, esquerda, direita);
        return camada;
    }

    private Pane criarFolhaShoji(boolean folhaDireita) {
        double largura = LARGURA_JANELA / 2.0 + 2;
        double altura = ALTURA_JANELA;
        Pane folha = new Pane();
        folha.setPrefSize(largura, altura);
        folha.setMinSize(largura, altura);
        folha.setMaxSize(largura, altura);

        Rectangle papel = new Rectangle(largura, altura);
        papel.setFill(Color.web("#e8d7b2"));
        papel.setStroke(Color.web("#291b14"));
        papel.setStrokeWidth(10);
        folha.getChildren().add(papel);

        Color madeira = Color.web("#4a2b1b");
        for (int coluna = 1; coluna < 6; coluna++) {
            Rectangle barra = new Rectangle(8, altura);
            barra.setFill(madeira);
            barra.setLayoutX(coluna * largura / 6.0 - 4);
            folha.getChildren().add(barra);
        }
        for (int linha = 1; linha < 8; linha++) {
            Rectangle barra = new Rectangle(largura, 8);
            barra.setFill(madeira);
            barra.setLayoutY(linha * altura / 8.0 - 4);
            folha.getChildren().add(barra);
        }

        Rectangle molduraCentral = new Rectangle(18, altura);
        molduraCentral.setFill(Color.web("#24150f"));
        molduraCentral.setLayoutX(folhaDireita ? 0 : largura - 18);

        Rectangle puxador = new Rectangle(12, 44);
        puxador.setArcWidth(8);
        puxador.setArcHeight(8);
        puxador.setFill(Color.web("#b48238"));
        puxador.setStroke(Color.web("#382214"));
        puxador.setStrokeWidth(3);
        puxador.setLayoutX(folhaDireita ? 25 : largura - 37);
        puxador.setLayoutY(altura / 2.0 - 22);
        folha.getChildren().addAll(molduraCentral, puxador);
        return folha;
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

    private String textoControleKatana() {
        return inventarioKatana
                ? nomeTecla(AcaoControle.KATANA) + ": katana"
                : "Katana: aguarde a 2ª investida";
    }

    /** Desenha a grade de colisões e as rotas atuais calculadas pelo A*. */
    private void atualizarDepuracaoNavegacao(
            Canvas painel,
            CarregadorMapa mapa,
            List<GuardaPatrulha> guardas,
            Point2D ultimaPosicaoVista,
            boolean alertaAtivo
    ) {
        GraphicsContext pincel = painel.getGraphicsContext2D();
        pincel.clearRect(0, 0, painel.getWidth(), painel.getHeight());

        int linhas = mapa.obterQuantidadeLinhasNavegacao();
        int colunas = mapa.obterQuantidadeColunasNavegacao();
        int larguraTile = mapa.obterLarguraTileNavegacao();
        int alturaTile = mapa.obterAlturaTileNavegacao();

        pincel.setStroke(Color.rgb(225, 240, 255, 0.16));
        pincel.setLineWidth(0.6);

        for (int linha = 0; linha < linhas; linha++) {
            for (int coluna = 0; coluna < colunas; coluna++) {
                boolean bloqueado =
                        mapa.tileNavegacaoBloqueado(linha, coluna);
                double x = coluna * larguraTile;
                double y = linha * alturaTile;

                pincel.setFill(bloqueado
                        ? Color.rgb(255, 35, 35, 0.30)
                        : Color.rgb(35, 235, 115, 0.055));
                pincel.fillRect(x, y, larguraTile, alturaTile);
                pincel.strokeRect(x, y, larguraTile, alturaTile);
            }
        }

        for (int indice = 0; indice < guardas.size(); indice++) {
            GuardaPatrulha guarda = guardas.get(indice);
            List<Point2D> caminho = guarda.obterCaminhoParaDepuracao();
            Color cor = Color.hsb((indice * 79.0) % 360, 0.88, 1.0, 0.95);

            pincel.setStroke(cor);
            pincel.setFill(cor);
            pincel.setLineWidth(3.0);

            if (!caminho.isEmpty()) {
                pincel.beginPath();
                pincel.moveTo(caminho.get(0).getX(), caminho.get(0).getY());

                for (int ponto = 1; ponto < caminho.size(); ponto++) {
                    pincel.lineTo(
                            caminho.get(ponto).getX(),
                            caminho.get(ponto).getY()
                    );
                }

                pincel.stroke();
                Point2D destino = caminho.get(caminho.size() - 1);
                pincel.fillOval(
                        destino.getX() - 6,
                        destino.getY() - 6,
                        12,
                        12
                );
            }

            Point2D posicaoGuarda = guarda.obterPosicao();
            String nome = guarda.getId() == null
                    ? "guarda " + (indice + 1)
                    : guarda.getId();
            pincel.fillText(
                    nome,
                    posicaoGuarda.getX() + 10,
                    posicaoGuarda.getY() - 10
            );
        }

        if (alertaAtivo && ultimaPosicaoVista != null) {
            pincel.setStroke(Color.ORANGE);
            pincel.setFill(Color.ORANGE);
            pincel.setLineWidth(4.0);
            double x = ultimaPosicaoVista.getX();
            double y = ultimaPosicaoVista.getY();
            pincel.strokeOval(x - 10, y - 10, 20, 20);
            pincel.strokeLine(x - 14, y, x + 14, y);
            pincel.strokeLine(x, y - 14, x, y + 14);
            pincel.fillText("última posição vista", x + 16, y - 12);
        }
    }

    private Image carregarSpriteJogador(String caminho) {
        var endereco = getClass().getResource(caminho);

        if (endereco == null) {
            throw new IllegalStateException(
                    "Sprite do jogador não encontrado: " + caminho
            );
        }

        return new Image(endereco.toExternalForm());
    }

    /** Mantém a arte maior centralizada sobre a caixa de colisão de 32 px. */
    private ImageView criarVisualJogador(
            Rectangle jogador,
            Image spriteCaminhada
    ) {
        ImageView visual = new ImageView(spriteCaminhada);
        // A célula da folha mede 360x320. Essa proporção evita esticar o
        // personagem e a filtragem suave reduz a tremulação ao se mover.
        visual.setFitWidth(72);
        visual.setFitHeight(64);
        visual.setPreserveRatio(true);
        visual.setSmooth(true);
        visual.setMouseTransparent(true);
        visual.setManaged(false);
        visual.xProperty().bind(jogador.xProperty().subtract(20));
        visual.yProperty().bind(jogador.yProperty().subtract(32));
        definirQuadroSprite(visual, spriteCaminhada, 3, 0);
        return visual;
    }

    private void iniciarAcaoVisualJogador(
            int acao,
            int[] acaoAtual,
            int[] quadroAtual,
            double[] tempoQuadro,
            double[] tempoAcao
    ) {
        acaoAtual[0] = acao;
        quadroAtual[0] = 0;
        tempoQuadro[0] = 0;
        // O arremesso precisa de mais tempo para preparação e soltura.
        tempoAcao[0] = acao == 2 ? 0.72 : 0.56;
    }

    /** Seleciona automaticamente caminhada, katana ou arremesso. */
    private void atualizarAnimacaoJogador(
            ImageView visual,
            Image caminhada,
            Image katana,
            Image shuriken,
            Point2D direcao,
            boolean movendo,
            boolean derrotado,
            double tempoDecorrido,
            int[] acaoAtual,
            int[] quadroAtual,
            double[] tempoQuadro,
            double[] tempoAcao,
            double[] tempoDano
    ) {
        if (derrotado) {
            visual.setRotate(90);
            visual.setOpacity(0.62);
            return;
        }

        visual.setRotate(0);
        int linha = linhaSpriteParaDirecao(direcao);
        Image folhaAtual = caminhada;
        // Aproximadamente nove quadros por segundo deixa a corrida fluida
        // sem acelerar as ações de katana e shuriken.
        double intervaloQuadro = 0.11;

        if (tempoAcao[0] > 0) {
            folhaAtual = acaoAtual[0] == 1 ? katana : shuriken;
            intervaloQuadro = acaoAtual[0] == 2 ? 0.18 : 0.14;
            tempoAcao[0] = Math.max(0, tempoAcao[0] - tempoDecorrido);
        } else if (!movendo) {
            quadroAtual[0] = 0;
            tempoQuadro[0] = 0;
        }

        if (tempoAcao[0] > 0 || movendo) {
            tempoQuadro[0] += tempoDecorrido;

            while (tempoQuadro[0] >= intervaloQuadro) {
                tempoQuadro[0] -= intervaloQuadro;
                quadroAtual[0] = tempoAcao[0] > 0
                        ? Math.min(3, quadroAtual[0] + 1)
                        : (quadroAtual[0] + 1) % 4;
            }
        }

        if (tempoAcao[0] == 0 && acaoAtual[0] != 0) {
            acaoAtual[0] = 0;
            quadroAtual[0] = 0;
            tempoQuadro[0] = 0;
            folhaAtual = caminhada;
        }

        if (visual.getImage() != folhaAtual) {
            visual.setImage(folhaAtual);
        }
        definirQuadroSprite(
                visual,
                folhaAtual,
                linha,
                quadroAtual[0]
        );

        if (tempoDano[0] > 0) {
            tempoDano[0] = Math.max(0, tempoDano[0] - tempoDecorrido);
            boolean apagado = ((int) (tempoDano[0] * 35)) % 2 == 0;
            visual.setOpacity(apagado ? 0.30 : 1.0);
        } else {
            visual.setOpacity(1.0);
        }
    }

    private int linhaSpriteParaDirecao(Point2D direcao) {
        if (Math.abs(direcao.getX()) > Math.abs(direcao.getY())) {
            return direcao.getX() < 0 ? 1 : 2;
        }
        return direcao.getY() > 0 ? 0 : 3;
    }

    private void definirQuadroSprite(
            ImageView visual,
            Image folha,
            int linha,
            int coluna
    ) {
        double larguraQuadro = folha.getWidth() / 4.0;
        double alturaQuadro = folha.getHeight() / 4.0;
        visual.setViewport(new Rectangle2D(
                coluna * larguraQuadro,
                linha * alturaQuadro,
                larguraQuadro,
                alturaQuadro
        ));
    }

    private boolean algumGuardaOuvePasso(
            List<GuardaPatrulha> guardas,
            Point2D posicaoPasso,
            double alcance
    ) {
        return encontrarGuardaQueOuvePasso(
                guardas, posicaoPasso, alcance
        ) != null;
    }

    private GuardaPatrulha encontrarGuardaQueOuvePasso(
            List<GuardaPatrulha> guardas,
            Point2D posicaoPasso,
            double alcance
    ) {
        GuardaPatrulha maisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        for (GuardaPatrulha guarda : guardas) {
            Point2D posicaoGuarda = guarda.obterPosicao();
            double distancia = posicaoGuarda.distance(posicaoPasso);

            // Som próximo contorna paredes e objetos; visão continua bloqueada.
            if (guarda.consegueOuvir(posicaoPasso, alcance)
                    && distancia < menorDistancia) {
                maisProximo = guarda;
                menorDistancia = distancia;
            }
        }
        return maisProximo;
    }

    private String textoEtapaTutorial(int etapa) {
        return switch (etapa) {
            case 0 -> nomeTecla(AcaoControle.CIMA) + ", "
                    + nomeTecla(AcaoControle.ESQUERDA) + ", "
                    + nomeTecla(AcaoControle.BAIXO) + ", "
                    + nomeTecla(AcaoControle.DIREITA)
                    + ": movimente o personagem.  Pressione "
                    + nomeTecla(AcaoControle.PULAR_TUTORIAL)
                    + " para pular o tutorial.";
            case 1 -> "Segure " + nomeTecla(AcaoControle.FURTIVO)
                    + " enquanto anda para se mover devagar e "
                    + "fazer menos barulho.";
            case 2 -> "Agora solte " + nomeTecla(AcaoControle.FURTIVO)
                    + " e ande perto de um guarda. O círculo dourado mostra "
                    + "até onde seus passos podem ser ouvidos.";
            case 3 -> "Funcionou: ? significa que o guarda ouviu e está "
                    + "investigando. ! significa que ele enxergou você. "
                    + "Na fase 2 você aprenderá shurikens e fumaça.";
            default -> "";
        };
    }

    private String textoEtapaTutorialItens(int etapa) {
        return switch (etapa) {
            case 0 -> "FASE 2 — Aproxime-se de um baú e pressione "
                    + nomeTecla(AcaoControle.INTERAGIR)
                    + " para coletar equipamentos.  "
                    + nomeTecla(AcaoControle.PULAR_TUTORIAL)
                    + " pula este tutorial.";
            case 1 -> "SHURIKEN — pressione "
                    + nomeTecla(AcaoControle.SHURIKEN)
                    + " para lançar. Impactos em "
                    + "paredes podem distrair o guarda mais próximo.";
            case 2 -> "BOMBA DE FUMAÇA — pressione "
                    + nomeTecla(AcaoControle.FUMACA)
                    + ". Ela bloqueia a visão "
                    + "por 5 segundos, mas a explosão produz bastante ruído.";
            case 3 -> "KIT MÉDICO — pressione "
                    + nomeTecla(AcaoControle.KIT_MEDICO)
                    + " quando estiver ferido para "
                    + "recuperar 30 pontos de vida.";
            default -> "";
        };
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

    @Override
    public void stop() {
        somPassos.fechar();
    }
}
