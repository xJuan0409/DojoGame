package com.dojogame;

import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Responsável por localizar, interpretar e desenhar
 * os mapas criados no programa Tiled.
 */
public class CarregadorMapa {

    // Identificador utilizado pelas pequenas árvores no tileset atual.
    private static final int GID_ARVORE = 116;

    // Grade que informa quais posições do mapa bloqueiam o jogador.
    private boolean[][] blocosComColisao;

    // Obstáculos em pixels usados pelo dojo, cuja arte não segue a grade.
    private final List<Rectangle2D> obstaculosDojo = new ArrayList<>();
    private PixelReader leitorColisaoDojo;
    private int larguraArteDojo;
    private int alturaArteDojo;
    // A sala de treino usa objetos menores que um tile: para a visão dos
    // guardas, usamos os retângulos exatos e evitamos cortes quadrados.
    private boolean usaVisaoPrecisaDojo;

    // Medidas utilizadas para converter pixels em linhas e colunas.
    private int larguraTileMapa;
    private int alturaTileMapa;

    // Folga usada pelo centro dos guardas ao planejar rotas. Marcar qualquer
    // tile apenas tocado por um objeto criava paredes invisíveis no A*.
    private static final double MARGEM_NAVEGACAO_GUARDA = 9.0;

    /*
     * Guarda os pontos criados na camada PontosImportantes do Tiled.
     * A chave é o nome do objeto, como "spawn_jogador".
     */
    private final Map<String, Point2D> pontosImportantes = new HashMap<>();

    // Rotas desenhadas como polylines na camada RotasGuardas do Tiled.
    private final Map<String, List<Point2D>> rotasGuardas =
            new LinkedHashMap<>();

    // Baús e outros objetos posicionados na camada ItensInterativos.
    private final List<ObjetoInterativo> itensInterativos = new ArrayList<>();

    /**
     * Procura um mapa dentro da pasta de recursos "maps".
     *
     * @param nomeArquivo nome do arquivo, incluindo a extensão .tmx.
     * @return endereço do mapa encontrado.
     */
    public URL localizarMapa(String nomeArquivo) {
        String caminhoMapa = "/maps/" + nomeArquivo;
        URL enderecoMapa = getClass().getResource(caminhoMapa);

        if (enderecoMapa == null) {
            throw new IllegalStateException(
                    "O mapa não foi encontrado: " + caminhoMapa
            );
        }

        return enderecoMapa;
    }

    /**
     * Carrega um arquivo TMX e transforma suas camadas de tiles
     * em uma imagem desenhada sobre um Canvas do JavaFX.
     *
     * @param nomeArquivo nome do mapa que será carregado.
     * @return Canvas contendo o mapa desenhado.
     */
    public Canvas carregarVisualMapa(String nomeArquivo) {
        URL enderecoMapa = localizarMapa(nomeArquivo);
        Document documentoMapa = lerDocumentoXml(enderecoMapa);
        Element mapa = documentoMapa.getDocumentElement();
        obstaculosDojo.clear();
        leitorColisaoDojo = null;
        usaVisaoPrecisaDojo = false;

        // Lê as medidas gerais definidas no Tiled.
        int larguraEmTiles = lerInteiro(mapa, "width");
        int alturaEmTiles = lerInteiro(mapa, "height");
        larguraTileMapa = lerInteiro(mapa, "tilewidth");
        alturaTileMapa = lerInteiro(mapa, "tileheight");

        // Lê os pontos especiais antes de criar os elementos do jogo.
        carregarPontosImportantes(mapa);
        carregarItensInterativos(mapa);

        // Lê também os caminhos que serão percorridos pelos guardas.
        // Mapas sem guardas, como o armazém, podem omitir essa camada.
        carregarRotasGuardas(mapa);

        // Inicialmente nenhuma posição do mapa possui colisão.
        blocosComColisao = new boolean[alturaEmTiles][larguraEmTiles];

        // Calcula o tamanho total do mapa em pixels.
        int larguraEmPixels = larguraEmTiles * larguraTileMapa;
        int alturaEmPixels = alturaEmTiles * alturaTileMapa;

        // A arte final do dojo ocupa exatamente uma tela do jogo.
        if (ehMapaDojo(mapa)) {
            alturaEmPixels = 640;
        }

        // O Canvas funciona como a tela sobre a qual os tiles são desenhados.
        Canvas canvasMapa = new Canvas(larguraEmPixels, alturaEmPixels);
        GraphicsContext pincel = canvasMapa.getGraphicsContext2D();

        // Carrega as imagens e informações de todos os tilesets externos.
        List<DadosTileset> tilesets = carregarTilesets(enderecoMapa, mapa);

        /*
         * Procura somente camadas de tiles. Camadas de objetos,
         * como PontosImportantes e RotasGuardas, ficam para outra etapa.
         */
        NodeList camadas = mapa.getElementsByTagName("layer");

        // Desenha as camadas na mesma ordem em que aparecem no Tiled.
        for (int indice = 0; indice < camadas.getLength(); indice++) {
            Element camada = (Element) camadas.item(indice);

            // Uma camada com visible="0" está oculta e não deve aparecer.
            if ("0".equals(camada.getAttribute("visible"))) {
                continue;
            }

            // Verifica a propriedade personalizada colisao=true do Tiled.
            boolean camadaTemColisao = camadaPossuiColisao(camada);

            desenharCamada(
                    pincel,
                    camada,
                    tilesets,
                    larguraTileMapa,
                    alturaTileMapa,
                    camadaTemColisao
            );
        }

        // O novo dojo usa uma composição própria: madeira, tatames e shoji.
        // A camada de colisão continua sendo lida do TMX normalmente.
        if (ehMapaDojo(mapa)) {
            String tema = mapa.getAttribute("tema");
            desenharVisualDojo(pincel, larguraEmPixels, alturaEmPixels, tema);
            int linhasVisiveis = (int) Math.ceil(
                    alturaEmPixels / (double) alturaTileMapa
            );
            configurarColisoesDojo(larguraEmTiles, linhasVisiveis, tema);

            // Baús visíveis também ocupam espaço físico. Eles ficam fora
            // dos corredores principais para nunca bloquear a progressão.
            for (ObjetoInterativo item : itensInterativos) {
                if (item.nome().toLowerCase().startsWith("bau_")) {
                    adicionarObstaculoDojo(
                            item.x(),
                            item.y(),
                            Math.max(28, item.largura()),
                            Math.max(24, item.altura())
                    );
                }
            }
        }

        return canvasMapa;
    }

    /** Desenha a decoração fixa do dojo sem alterar as colisões do mapa. */
    private boolean ehMapaDojo(Element mapa) {
        String tema = mapa.getAttribute("tema");
        return "dojo_japones".equals(tema)
                || "sala_treinamento".equals(tema)
                || "patio_noturno".equals(tema)
                || "arena_subchefe".equals(tema)
                || "arena_chefao_final".equals(tema)
                || tema.startsWith("sala_lateral_")
                || tema.startsWith("sequencia_final_");
    }

    private void desenharVisualDojo(
            GraphicsContext pincel,
            int largura,
            int altura,
            String tema
    ) {
        String arquivoArte = "sala_treinamento".equals(tema)
                ? "/maps/sala_treinamento_visual.png"
                : "patio_noturno".equals(tema)
                ? "/maps/patio_noturno_visual.png"
                : "sala_lateral_deposito".equals(tema)
                ? "/maps/sala_lateral_deposito_visual.png"
                : "sala_lateral_guarda".equals(tema)
                ? "/maps/sala_lateral_guarda_visual.png"
                : "sala_lateral_vazia".equals(tema)
                ? "/maps/sala_lateral_vazia_visual.png"
                : "sequencia_final_1".equals(tema)
                ? "/maps/sequencia_final_1_visual.png"
                : "sequencia_final_2".equals(tema)
                ? "/maps/sequencia_final_2_visual.png"
                : "sequencia_final_3".equals(tema)
                ? "/maps/sequencia_final_3_visual.png"
                : "arena_subchefe".equals(tema)
                ? "/maps/arena_subchefe_visual.png"
                : "arena_chefao_final".equals(tema)
                ? "/maps/arena_chefao_final_visual.png"
                : "/maps/dojo_visual_reference.png";
        URL referencia = getClass().getResource(arquivoArte);

        if (referencia != null) {
            Image arteDojo = new Image(referencia.toExternalForm());
            pincel.drawImage(arteDojo, 0, 0, largura, altura);
            return;
        }

        // Fundo escuro, como a moldura da referência.
        pincel.setFill(Color.web("#1c1514"));
        pincel.fillRect(0, 0, largura, altura);
        // Salão principal à esquerda; corredor e duas salas à direita.
        desenharSalaTatami(pincel, 32, 44, 544, 628, 3, 4);
        desenharSalaTatami(pincel, 618, 56, 310, 260, 2, 2);
        desenharSalaTatami(pincel, 618, 382, 310, 224, 2, 2);

        // Faixas de madeira que delimitam as três salas.
        desenharMolduraSala(pincel, 28, 28, 552, 660);
        desenharMolduraSala(pincel, 606, 40, 334, 292);
        desenharMolduraSala(pincel, 606, 366, 334, 256);
        pincel.setFill(Color.web("#3b2119"));
        pincel.fillRect(580, 116, 26, 444);
        pincel.setFill(Color.web("#b87943"));
        pincel.fillRect(588, 116, 6, 444);

        // Altar elevado no alto do salão principal.
        pincel.setFill(Color.web("#54271f"));
        pincel.fillRoundRect(166, 52, 276, 88, 7, 7);
        pincel.setFill(Color.web("#d7b06d"));
        pincel.fillRoundRect(258, 62, 92, 54, 5, 5);
        pincel.setFill(Color.web("#3a2118"));
        pincel.fillText("道", 296, 98);
        pincel.setFill(Color.web("#a72d22"));
        pincel.fillRect(184, 50, 16, 94);
        pincel.fillRect(408, 50, 16, 94);

        // Portas, janelas shoji, lanternas e suportes de armas.
        desenharShoji(pincel, 48, 56, 104, 62);
        desenharShoji(pincel, 462, 56, 96, 62);
        desenharShoji(pincel, 634, 66, 120, 54);
        desenharShoji(pincel, 772, 66, 138, 54);
        desenharShoji(pincel, 636, 400, 120, 54);
        desenharShoji(pincel, 772, 400, 138, 54);
        desenharShoji(pincel, 248, 656, 104, 32);
        desenharLanterna(pincel, 72, 188);
        desenharLanterna(pincel, 540, 188);
        desenharLanterna(pincel, 642, 272);
        desenharLanterna(pincel, 900, 272);
        desenharLanterna(pincel, 642, 562);
        desenharLanterna(pincel, 900, 562);
        desenharSuporteArmas(pincel, 76, 318);
        desenharSuporteArmas(pincel, 438, 318);
        desenharSuporteArmas(pincel, 820, 170);
        desenharSuporteKatanas(pincel, 94, 182);
        desenharSuporteKatanas(pincel, 406, 182);
        desenharSuporteKatanas(pincel, 812, 290);
        desenharVasoComPlanta(pincel, 96, 624);
        desenharVasoComPlanta(pincel, 506, 624);
        desenharVasoComPlanta(pincel, 652, 298);
        desenharVasoComPlanta(pincel, 894, 298);
        desenharVasoComPlanta(pincel, 652, 588);
        desenharVasoComPlanta(pincel, 894, 588);
        desenharMesaBaixa(pincel, 118, 510);
        desenharMesaBaixa(pincel, 412, 510);
        desenharMesaBaixa(pincel, 740, 514);
        desenharPergaminho(pincel, 208, 158, "忍");
        desenharPergaminho(pincel, 376, 158, "武");
        desenharPergaminho(pincel, 784, 132, "静");
        desenharBancos(pincel, 204, 600);
        desenharBancos(pincel, 702, 580);

        // Artes pixeladas do conjunto japonês do projeto: acrescentam os
        // detalhes de cena sem interferir em paredes ou rotas de guarda.
        desenharPropDojo(pincel, "/tilesets/dojo/katanas.png", 64, 232,
                112, 114);
        desenharPropDojo(pincel, "/tilesets/dojo/katanas.png", 794, 172,
                102, 104);
        desenharPropDojo(pincel, "/tilesets/dojo/armadura.png", 468, 146,
                64, 90);
        desenharPropDojo(pincel, "/tilesets/dojo/barris.png", 438, 236,
                72, 78);
        desenharPropDojo(pincel, "/tilesets/dojo/cha.png", 714, 474,
                108, 92);

        // Emblema central e degraus da entrada.
        pincel.setStroke(Color.web("#6c432a"));
        pincel.setLineWidth(8);
        pincel.strokeOval(250, 376, 104, 104);
        pincel.setStroke(Color.web("#c1a66c"));
        pincel.setLineWidth(3);
        pincel.strokeOval(268, 394, 68, 68);
        pincel.setFill(Color.web("#5a3827"));
        pincel.fillRect(224, 682, 152, 18);
        pincel.fillRect(238, 702, 124, 18);
        pincel.fillRect(252, 722, 96, 18);
    }

    /** Faz as paredes visuais do dojo bloquearem a movimentação. */
    private void configurarColisoesDojo(
            int colunas, int linhas, String tema
    ) {
        blocosComColisao = new boolean[linhas][colunas];
        if ("sala_treinamento".equals(tema)) {
            usaVisaoPrecisaDojo = true;
            configurarColisoesSalaTreinamento();
            return;
        }
        if ("patio_noturno".equals(tema)) {
            usaVisaoPrecisaDojo = true;
            configurarColisoesPatioNoturno();
            return;
        }
        if (tema.startsWith("sala_lateral_")
                || tema.startsWith("sequencia_final_")) {
            usaVisaoPrecisaDojo = true;
            configurarColisoesSalaLateral(tema);
            return;
        }
        if ("arena_subchefe".equals(tema)
                || "arena_chefao_final".equals(tema)) {
            usaVisaoPrecisaDojo = true;
            configurarColisoesArenaSubchefe();
            return;
        }
        if ("dojo_japones".equals(tema)) {
            usaVisaoPrecisaDojo = true;
            configurarColisoesDojoPrincipal();
            return;
        }
        URL enderecoArte = getClass().getResource(
                "/maps/dojo_visual_reference.png"
        );

        if (enderecoArte == null) {
            return;
        }

        Image arte = new Image(enderecoArte.toExternalForm());
        leitorColisaoDojo = arte.getPixelReader();
        larguraArteDojo = (int) arte.getWidth();
        alturaArteDojo = (int) arte.getHeight();

        // Bonecos de treino: a madeira clara não é escura o bastante para
        // a leitura automática, então estes volumes são explícitos.
        adicionarObstaculoDojo(462, 310, 34, 72);
        adicionarObstaculoDojo(500, 310, 34, 72);
        adicionarObstaculoDojo(822, 218, 36, 74);
        adicionarObstaculoDojo(874, 228, 36, 74);
    }

    /**
     * Colisões da terceira fase. A versão anterior inferia paredes pela cor
     * da imagem; o piso de madeira escuro era confundido com objetos sólidos.
     */
    private void configurarColisoesDojoPrincipal() {
        // Limites do salão principal, mantendo abertas a porta superior
        // esquerda, a entrada inferior e o corredor para a ala direita.
        adicionarObstaculoDojo(0, 0, 46, 640);
        adicionarObstaculoDojo(200, 0, 380, 178);
        adicionarObstaculoDojo(0, 0, 46, 178);
        adicionarObstaculoDojo(0, 548, 214, 92);
        adicionarObstaculoDojo(438, 548, 186, 92);
        adicionarObstaculoDojo(580, 0, 44, 214);
        adicionarObstaculoDojo(580, 350, 44, 290);

        // Moldura da ala direita. O gatilho da porta superior permanece
        // acessível por baixo, e o piso das duas salas não vira obstáculo.
        adicionarObstaculoDojo(624, 40, 336, 70);
        adicionarObstaculoDojo(936, 40, 24, 600);
        adicionarObstaculoDojo(624, 350, 336, 30);
        adicionarObstaculoDojo(624, 596, 336, 44);

        // Altar e móveis encostados nas paredes do salão principal.
        adicionarObstaculoDojo(246, 72, 190, 108);
        adicionarObstaculoDojo(22, 174, 94, 120);
        adicionarObstaculoDojo(20, 292, 72, 82);
        adicionarObstaculoDojo(22, 406, 72, 126);

        // Bonecos de treino visíveis na área livre.
        adicionarObstaculoDojo(204, 216, 32, 62);
        adicionarObstaculoDojo(466, 300, 32, 66);
        adicionarObstaculoDojo(500, 298, 32, 68);

        // Objetos sólidos nas duas salas da ala direita.
        adicionarObstaculoDojo(638, 132, 52, 82);
        adicionarObstaculoDojo(902, 134, 34, 118);
        adicionarObstaculoDojo(648, 310, 184, 42);
        adicionarObstaculoDojo(894, 292, 42, 58);
        adicionarObstaculoDojo(704, 488, 116, 78);
        adicionarObstaculoDojo(626, 448, 48, 78);
        adicionarObstaculoDojo(912, 438, 24, 92);
    }

    /** Colisões da sala de treino, alinhadas aos objetos da arte. */
    private void configurarColisoesSalaTreinamento() {
        // Paredes externas. As aberturas centralizadas ficam livres.
        adicionarObstaculoDojo(0, 0, 438, 54);
        adicionarObstaculoDojo(522, 0, 438, 54);
        adicionarObstaculoDojo(0, 0, 42, 640);
        adicionarObstaculoDojo(918, 0, 42, 640);
        adicionarObstaculoDojo(0, 548, 382, 92);
        adicionarObstaculoDojo(578, 548, 382, 92);

        // Sacos de treino e seus suportes. As áreas seguem apenas o desenho
        // sólido de cada objeto, sem criar uma margem invisível ao redor.
        adicionarObstaculoDojo(286, 92, 48, 78);
        adicionarObstaculoDojo(630, 92, 48, 78);
        adicionarObstaculoDojo(462, 142, 48, 96);
        adicionarObstaculoDojo(246, 280, 48, 92);
        adicionarObstaculoDojo(683, 280, 48, 92);

        // Divisórias usadas como cobertura.
        adicionarObstaculoDojo(238, 182, 158, 48);
        adicionarObstaculoDojo(570, 182, 158, 48);
        adicionarObstaculoDojo(154, 392, 202, 48);
        adicionarObstaculoDojo(600, 392, 202, 48);

        // Cada caixa fica com a própria colisão, deixando livres os espaços
        // entre elas para que o personagem nunca fique preso em um objeto
        // fantasma.
        adicionarObstaculoDojo(620, 54, 40, 46);
        adicionarObstaculoDojo(684, 60, 42, 42);
        adicionarObstaculoDojo(66, 313, 48, 40);
        adicionarObstaculoDojo(67, 354, 44, 34);
        adicionarObstaculoDojo(84, 447, 44, 42);
        adicionarObstaculoDojo(84, 489, 44, 42);
        adicionarObstaculoDojo(122, 478, 44, 54);
        adicionarObstaculoDojo(164, 478, 44, 54);
        adicionarObstaculoDojo(850, 247, 58, 72);
        adicionarObstaculoDojo(848, 342, 52, 42);
        adicionarObstaculoDojo(850, 384, 52, 34);
        adicionarObstaculoDojo(832, 500, 42, 34);
        adicionarObstaculoDojo(875, 501, 44, 34);

        // Estantes encostadas nas paredes.
        adicionarObstaculoDojo(66, 58, 182, 44);
        adicionarObstaculoDojo(744, 58, 146, 44);
    }

    /** Colisões precisas do pátio: muros, caixas, bambus e poço. */
    private void configurarColisoesPatioNoturno() {
        // Moldura externa, mantendo livres as duas portas centrais.
        adicionarObstaculoDojo(0, 0, 432, 126);
        adicionarObstaculoDojo(530, 0, 430, 126);
        adicionarObstaculoDojo(0, 0, 52, 640);
        adicionarObstaculoDojo(908, 0, 52, 640);
        adicionarObstaculoDojo(0, 468, 390, 172);
        adicionarObstaculoDojo(570, 468, 390, 172);

        // Vegetação encostada nas laterais. Antes, o bloco direito começava
        // em x=780 e invadia muito piso livre; agora acompanha só o bambuzal.
        adicionarObstaculoDojo(52, 126, 94, 98);
        adicionarObstaculoDojo(842, 126, 66, 104);

        // Lago e pedras da margem esquerda.
        adicionarObstaculoDojo(82, 130, 126, 122);
        adicionarObstaculoDojo(106, 244, 100, 42);
        adicionarObstaculoDojo(184, 154, 45, 82);

        // Muros baixos, separados por peça para não criarem áreas fantasmas.
        adicionarObstaculoDojo(304, 149, 127, 59);
        adicionarObstaculoDojo(346, 273, 100, 51);
        adicionarObstaculoDojo(176, 337, 170, 50);
        adicionarObstaculoDojo(562, 226, 18, 139);
        adicionarObstaculoDojo(843, 252, 89, 55);
        adicionarObstaculoDojo(715, 361, 38, 107);

        // Cada pilha de caixas possui seu próprio volume visível.
        adicionarObstaculoDojo(307, 162, 33, 60);
        adicionarObstaculoDojo(314, 261, 32, 61);
        adicionarObstaculoDojo(108, 337, 50, 82);
        adicionarObstaculoDojo(145, 367, 36, 54);
        adicionarObstaculoDojo(529, 215, 35, 52);
        adicionarObstaculoDojo(571, 307, 72, 64);
        adicionarObstaculoDojo(664, 102, 65, 57);

        // Poço, barril e elementos sólidos do jardim direito.
        adicionarObstaculoDojo(622, 162, 116, 126);
        adicionarObstaculoDojo(747, 218, 38, 64);
        adicionarObstaculoDojo(795, 336, 37, 41);
        adicionarObstaculoDojo(842, 321, 57, 96);
        adicionarObstaculoDojo(680, 397, 54, 69);

        // Lanternas e agrupamentos inferiores, fora do corredor central.
        adicionarObstaculoDojo(367, 410, 42, 58);
        adicionarObstaculoDojo(557, 410, 42, 58);
        adicionarObstaculoDojo(52, 382, 138, 86);
    }

    /** Colisões das salas pequenas, mantendo um corredor central livre. */
    private void configurarColisoesSalaLateral(String tema) {
        // Paredes externas e abertura larga na porta inferior.
        boolean saidaSuperior = tema.startsWith("sequencia_final_");
        if (saidaSuperior) {
            adicionarObstaculoDojo(0, 0, 404, 48);
            adicionarObstaculoDojo(556, 0, 404, 48);
        } else {
            adicionarObstaculoDojo(0, 0, 960, 48);
        }
        adicionarObstaculoDojo(0, 0, 44, 640);
        adicionarObstaculoDojo(916, 0, 44, 640);
        adicionarObstaculoDojo(0, 520, 382, 120);
        adicionarObstaculoDojo(578, 520, 382, 120);

        String temaVisual = "sequencia_final_1".equals(tema)
                ? "sala_lateral_deposito" : "sequencia_final_2".equals(tema)
                ? "sala_lateral_guarda" : "sequencia_final_3".equals(tema)
                ? "sala_lateral_vazia" : tema;

        // As salas do corredor final têm artes próprias e poucos objetos
        // encostados nas bordas. Mantemos o centro totalmente navegável;
        // baús continuam recebendo colisão individual ao serem carregados.
        if (saidaSuperior) {
            return;
        }

        if ("sala_lateral_deposito".equals(temaVisual)) {
            // Caixas separadas: nenhuma colisão cobre o piso entre elas.
            adicionarObstaculoDojo(62, 58, 112, 116);
            adicionarObstaculoDojo(238, 82, 74, 126);
            adicionarObstaculoDojo(62, 220, 112, 162);
            adicionarObstaculoDojo(228, 272, 68, 92);
            adicionarObstaculoDojo(632, 60, 112, 122);
            adicionarObstaculoDojo(786, 88, 82, 122);
            adicionarObstaculoDojo(642, 204, 76, 98);
            // Estante e fardos do lado direito. Antes havia um bloco grande
            // no piso livre inferior, causando colisão fantasma ao lado das
            // caixas e impedindo a rota pelo corredor direito.
            adicionarObstaculoDojo(774, 272, 138, 122);
            adicionarObstaculoDojo(846, 174, 68, 92);
            adicionarObstaculoDojo(228, 274, 76, 92);
            return;
        }

        if ("sala_lateral_guarda".equals(temaVisual)) {
            adicionarObstaculoDojo(142, 52, 178, 84);
            adicionarObstaculoDojo(650, 52, 184, 84);
            adicionarObstaculoDojo(198, 250, 214, 100);
            adicionarObstaculoDojo(52, 128, 76, 146);
            adicionarObstaculoDojo(856, 124, 58, 150);
            return;
        }

        // Na sala vazia, somente o altar superior bloqueia.
        adicionarObstaculoDojo(405, 48, 150, 108);
    }

    /** Colisões da arena, alinhadas aos pilares, biombos e caixas. */
    private void configurarColisoesArenaSubchefe() {
        // Paredes com abertura central na entrada e na saída.
        adicionarObstaculoDojo(0, 0, 404, 72);
        adicionarObstaculoDojo(556, 0, 404, 72);
        adicionarObstaculoDojo(0, 0, 48, 640);
        adicionarObstaculoDojo(912, 0, 48, 640);
        adicionarObstaculoDojo(0, 510, 392, 130);
        adicionarObstaculoDojo(568, 510, 392, 130);

        // Quatro pilares — volumes individuais, sem áreas fantasmas.
        adicionarObstaculoDojo(226, 140, 46, 86);
        adicionarObstaculoDojo(688, 140, 46, 86);
        adicionarObstaculoDojo(216, 340, 48, 108);
        adicionarObstaculoDojo(696, 340, 48, 108);

        // Biombos de cobertura.
        adicionarObstaculoDojo(196, 254, 112, 68);
        adicionarObstaculoDojo(666, 254, 112, 68);

        // Pilhas de caixas, cada grupo limitado ao desenho visível.
        adicionarObstaculoDojo(286, 166, 62, 54);
        adicionarObstaculoDojo(612, 166, 58, 54);
        adicionarObstaculoDojo(276, 392, 64, 56);
        adicionarObstaculoDojo(620, 392, 62, 56);

        // Altar e armaduras, preservando o vão da porta superior.
        adicionarObstaculoDojo(300, 48, 104, 66);
        adicionarObstaculoDojo(556, 48, 104, 66);
    }

    private void adicionarObstaculoDojo(
            double x, double y, double largura, double altura
    ) {
        obstaculosDojo.add(new Rectangle2D(x, y, largura, altura));

        // Registra o mesmo obstáculo na grade. Assim, os guardas também
        // planejam as rotas sem atravessar paredes, caixas ou sacos.
        if (blocosComColisao == null) {
            return;
        }

        double esquerdaSegura = x - MARGEM_NAVEGACAO_GUARDA;
        double direitaSegura = x + largura + MARGEM_NAVEGACAO_GUARDA;
        double cimaSeguro = y - MARGEM_NAVEGACAO_GUARDA;
        double baixoSeguro = y + altura + MARGEM_NAVEGACAO_GUARDA;

        int primeiraColuna = Math.max(0,
                (int) Math.floor(esquerdaSegura / larguraTileMapa));
        int ultimaColuna = Math.min(blocosComColisao[0].length - 1,
                (int) Math.floor(direitaSegura / larguraTileMapa));
        int primeiraLinha = Math.max(0,
                (int) Math.floor(cimaSeguro / alturaTileMapa));
        int ultimaLinha = Math.min(blocosComColisao.length - 1,
                (int) Math.floor(baixoSeguro / alturaTileMapa));

        for (int linha = primeiraLinha; linha <= ultimaLinha; linha++) {
            for (int coluna = primeiraColuna;
                 coluna <= ultimaColuna;
                 coluna++) {
                double centroX = (coluna + 0.5) * larguraTileMapa;
                double centroY = (linha + 0.5) * alturaTileMapa;

                // O A* movimenta o centro do guarda. Portanto, o tile só
                // precisa ser bloqueado quando esse centro entraria no
                // objeto (considerando a pequena folga acima), e não quando
                // uma quina distante do tile apenas toca a decoração.
                if (centroX >= esquerdaSegura
                        && centroX <= direitaSegura
                        && centroY >= cimaSeguro
                        && centroY <= baixoSeguro) {
                    blocosComColisao[linha][coluna] = true;
                }
            }
        }
    }

    private void liberarRetanguloDojo(
            int esquerda, int cima, int direita, int baixo
    ) {
        for (int linha = cima; linha <= baixo; linha++) {
            for (int coluna = esquerda; coluna <= direita; coluna++) {
                blocosComColisao[linha][coluna] = false;
            }
        }
    }

    private void bloquearRetanguloDojo(
            int esquerda, int cima, int direita, int baixo
    ) {
        for (int linha = cima; linha <= baixo; linha++) {
            for (int coluna = esquerda; coluna <= direita; coluna++) {
                blocosComColisao[linha][coluna] = true;
            }
        }
    }

    private void desenharMolduraSala(
            GraphicsContext pincel, double x, double y, double largura,
            double altura
    ) {
        pincel.setStroke(Color.web("#3b2119"));
        pincel.setLineWidth(18);
        pincel.strokeRect(x + 9, y + 9, largura - 18, altura - 18);
        pincel.setStroke(Color.web("#b87943"));
        pincel.setLineWidth(3);
        pincel.strokeRect(x + 8, y + 8, largura - 16, altura - 16);
    }

    private void desenharSalaTatami(
            GraphicsContext pincel, double x, double y, double largura,
            double altura, int colunas, int linhas
    ) {
        pincel.setFill(Color.web("#6b4329"));
        pincel.fillRect(x, y, largura, altura);
        double larguraTatami = (largura - 34) / colunas;
        double alturaTatami = (altura - 30) / linhas;
        for (int linha = 0; linha < linhas; linha++) {
            for (int coluna = 0; coluna < colunas; coluna++) {
                pincel.setFill((linha + coluna) % 2 == 0
                        ? Color.web("#d8c48d") : Color.web("#cdb77d"));
                double tatamiX = x + 17 + coluna * larguraTatami;
                double tatamiY = y + 15 + linha * alturaTatami;
                pincel.fillRect(tatamiX, tatamiY,
                        larguraTatami - 5, alturaTatami - 5);
                pincel.setStroke(Color.web("#5c774c"));
                pincel.setLineWidth(2);
                pincel.strokeRect(tatamiX, tatamiY,
                        larguraTatami - 5, alturaTatami - 5);
            }
        }
    }

    private void desenharShoji(
            GraphicsContext pincel, double x, double y, double largura,
            double altura
    ) {
        pincel.setFill(Color.web("#4a2b20"));
        pincel.fillRect(x, y, largura, altura);
        pincel.setFill(Color.web("#f0dfac"));
        pincel.fillRect(x + 5, y + 5, largura - 10, altura - 10);
        pincel.setStroke(Color.web("#6f4229"));
        pincel.setLineWidth(2);
        for (int coluna = 1; coluna < 4; coluna++) {
            double linhaX = x + coluna * largura / 4.0;
            pincel.strokeLine(linhaX, y + 5, linhaX, y + altura - 5);
        }
        pincel.strokeLine(x + 5, y + altura / 2.0,
                x + largura - 5, y + altura / 2.0);
    }

    private void desenharLanterna(GraphicsContext pincel, double x, double y) {
        pincel.setFill(Color.web("#4a2b20"));
        pincel.fillRect(x - 5, y - 27, 10, 54);
        pincel.setFill(Color.web("#e9b75d"));
        pincel.fillRoundRect(x - 11, y - 18, 22, 28, 5, 5);
        pincel.setStroke(Color.web("#7a4423"));
        pincel.strokeRect(x - 11, y - 18, 22, 28);
    }

    private void desenharSuporteArmas(
            GraphicsContext pincel, double x, double y
    ) {
        pincel.setStroke(Color.web("#3a2118"));
        pincel.setLineWidth(5);
        pincel.strokeLine(x, y, x + 72, y);
        pincel.strokeLine(x + 5, y + 14, x + 67, y + 14);
        pincel.setStroke(Color.web("#b9b2a0"));
        pincel.setLineWidth(2);
        pincel.strokeLine(x + 12, y - 8, x + 62, y + 22);
        pincel.strokeLine(x + 12, y + 22, x + 62, y - 8);
    }

    private void desenharVasoComPlanta(
            GraphicsContext pincel, double x, double y
    ) {
        pincel.setFill(Color.web("#355f3b"));
        pincel.fillOval(x - 22, y - 42, 24, 32);
        pincel.fillOval(x - 2, y - 52, 25, 38);
        pincel.fillOval(x + 10, y - 36, 25, 30);
        pincel.setStroke(Color.web("#213d2a"));
        pincel.setLineWidth(2);
        pincel.strokeOval(x - 22, y - 42, 24, 32);
        pincel.strokeOval(x - 2, y - 52, 25, 38);
        pincel.setFill(Color.web("#8a5535"));
        pincel.fillRoundRect(x - 13, y - 16, 28, 27, 6, 6);
        pincel.setStroke(Color.web("#4d2c20"));
        pincel.strokeRoundRect(x - 13, y - 16, 28, 27, 6, 6);
    }

    private void desenharPergaminho(
            GraphicsContext pincel, double x, double y, String simbolo
    ) {
        pincel.setFill(Color.web("#74342b"));
        pincel.fillRect(x - 4, y - 4, 36, 62);
        pincel.setFill(Color.web("#e8d5a0"));
        pincel.fillRect(x, y, 28, 53);
        pincel.setFill(Color.web("#321c16"));
        pincel.fillText(simbolo, x + 8, y + 33);
    }

    private void desenharSuporteKatanas(
            GraphicsContext pincel, double x, double y
    ) {
        pincel.setFill(Color.web("#3c241a"));
        pincel.fillRect(x, y, 82, 8);
        pincel.fillRect(x + 8, y + 8, 7, 28);
        pincel.fillRect(x + 67, y + 8, 7, 28);
        pincel.setStroke(Color.web("#c8c9c2"));
        pincel.setLineWidth(2);
        pincel.strokeLine(x + 10, y - 9, x + 65, y + 4);
        pincel.strokeLine(x + 17, y - 17, x + 73, y - 4);
        pincel.setStroke(Color.web("#b69144"));
        pincel.strokeLine(x + 18, y - 12, x + 24, y - 4);
        pincel.strokeLine(x + 25, y - 20, x + 31, y - 12);
    }

    private void desenharMesaBaixa(
            GraphicsContext pincel, double x, double y
    ) {
        pincel.setFill(Color.web("#7b472b"));
        pincel.fillRoundRect(x, y, 92, 23, 6, 6);
        pincel.fillRect(x + 9, y + 20, 8, 17);
        pincel.fillRect(x + 75, y + 20, 8, 17);
        pincel.setFill(Color.web("#e0c27e"));
        pincel.fillOval(x + 38, y + 4, 16, 10);
    }

    private void desenharBancos(
            GraphicsContext pincel, double x, double y
    ) {
        pincel.setFill(Color.web("#694027"));
        pincel.fillRoundRect(x, y, 134, 16, 4, 4);
        pincel.fillRect(x + 12, y + 14, 7, 18);
        pincel.fillRect(x + 114, y + 14, 7, 18);
        pincel.setStroke(Color.web("#bd8751"));
        pincel.setLineWidth(2);
        pincel.strokeLine(x + 8, y + 6, x + 126, y + 6);
    }

    private void desenharPropDojo(
            GraphicsContext pincel, String caminho, double x, double y,
            double largura, double altura
    ) {
        URL endereco = getClass().getResource(caminho);

        if (endereco == null) {
            return;
        }

        Image prop = new Image(endereco.toExternalForm());
        pincel.drawImage(prop, x, y, largura, altura);
    }

    /**
     * Lê os objetos da camada PontosImportantes criada no Tiled.
     *
     * Cada objeto encontrado é armazenado pelo seu nome e pelas
     * coordenadas X e Y registradas dentro do arquivo TMX.
     */
    private void carregarPontosImportantes(Element mapa) {
        // Limpa os pontos anteriores caso outro mapa seja carregado.
        pontosImportantes.clear();

        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {

            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            // Ignora as outras camadas, como RotasGuardas.
            if (!"PontosImportantes".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {

                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");

                // Um objeto sem nome não pode ser solicitado pelo jogo.
                if (nome.isBlank()) {
                    continue;
                }

                double x = Double.parseDouble(objeto.getAttribute("x"));
                double y = Double.parseDouble(objeto.getAttribute("y"));

                pontosImportantes.put(nome, new Point2D(x, y));
            }

            // Existe apenas uma camada PontosImportantes neste mapa.
            return;
        }

        throw new IllegalStateException(
                "A camada PontosImportantes não foi encontrada no mapa."
        );
    }

    /**
     * Obtém a posição de um ponto criado no Tiled.
     *
     * Este método deve ser chamado depois de carregarVisualMapa().
     *
     * @param nome nome exato do objeto dentro do Tiled.
     * @return coordenadas do ponto dentro do mapa.
     */
    public Point2D obterPontoImportante(String nome) {
        Point2D ponto = pontosImportantes.get(nome);

        if (ponto == null) {
            throw new IllegalStateException(
                    "O ponto importante não foi encontrado: " + nome
            );
        }

        return ponto;
    }


    /**
     * Lê os retângulos criados na camada ItensInterativos.
     */
    private void carregarItensInterativos(Element mapa) {
        itensInterativos.clear();
        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {
            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            if (!"ItensInterativos".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {
                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");

                if (nome.isBlank()) {
                    continue;
                }

                itensInterativos.add(new ObjetoInterativo(
                        nome,
                        Double.parseDouble(objeto.getAttribute("x")),
                        Double.parseDouble(objeto.getAttribute("y")),
                        lerDoubleOpcional(objeto, "width"),
                        lerDoubleOpcional(objeto, "height")
                ));
            }

            return;
        }
    }

    /**
     * Retorna os objetos interativos encontrados no mapa atual.
     */
    public List<ObjetoInterativo> obterItensInterativos() {
        return List.copyOf(itensInterativos);
    }

    private double lerDoubleOpcional(Element elemento, String atributo) {
        String valor = elemento.getAttribute(atributo);
        return valor.isBlank() ? 0.0 : Double.parseDouble(valor);
    }

    /**
     * Lê as polylines da camada RotasGuardas.
     *
     * Os pontos de uma polyline são relativos à posição X/Y do objeto.
     * Por isso, cada ponto é convertido para uma coordenada absoluta do mapa.
     */
    private void carregarRotasGuardas(Element mapa) {
        rotasGuardas.clear();

        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {

            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            if (!"RotasGuardas".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {

                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");
                NodeList polylines = objeto.getElementsByTagName("polyline");

                if (nome.isBlank() || polylines.getLength() == 0) {
                    continue;
                }

                double origemX = Double.parseDouble(objeto.getAttribute("x"));
                double origemY = Double.parseDouble(objeto.getAttribute("y"));

                Element polyline = (Element) polylines.item(0);
                String[] pontosTexto = polyline
                        .getAttribute("points")
                        .trim()
                        .split("\\s+");

                List<Point2D> pontosDaRota = new ArrayList<>();

                for (String pontoTexto : pontosTexto) {
                    String[] coordenadas = pontoTexto.split(",");

                    if (coordenadas.length != 2) {
                        continue;
                    }

                    double x = origemX + Double.parseDouble(coordenadas[0]);
                    double y = origemY + Double.parseDouble(coordenadas[1]);
                    pontosDaRota.add(new Point2D(x, y));
                }

                // Uma patrulha precisa de pelo menos origem e destino.
                if (pontosDaRota.size() >= 2) {
                    rotasGuardas.put(nome, List.copyOf(pontosDaRota));
                }
            }

            return;
        }

        // A ausência dessa camada significa apenas que o mapa não tem guardas.
    }

    /**
     * Retorna as rotas encontradas sem permitir alterações externas.
     */
    public Map<String, List<Point2D>> obterRotasGuardas() {
        return Collections.unmodifiableMap(rotasGuardas);
    }

    /** Quantidade de linhas disponíveis para a navegação dos inimigos. */
    public int obterQuantidadeLinhasNavegacao() {
        return blocosComColisao == null ? 0 : blocosComColisao.length;
    }

    /** Quantidade de colunas disponíveis para a navegação dos inimigos. */
    public int obterQuantidadeColunasNavegacao() {
        return blocosComColisao == null || blocosComColisao.length == 0
                ? 0
                : blocosComColisao[0].length;
    }

    public int obterLarguraTileNavegacao() {
        return larguraTileMapa;
    }

    public int obterAlturaTileNavegacao() {
        return alturaTileMapa;
    }

    /** Consulta segura usada somente pelo modo visual de depuração. */
    public boolean tileNavegacaoBloqueado(int linha, int coluna) {
        return posicaoDaGradeBloqueada(linha, coluna);
    }

    /**
     * Verifica se duas posições conseguem se enxergar sem uma parede no meio.
     */
    public boolean possuiLinhaDeVisao(Point2D origem, Point2D destino) {
        if (blocosComColisao == null) {
            return false;
        }

        Point2D deslocamento = destino.subtract(origem);
        double distancia = deslocamento.magnitude();

        if (distancia == 0) {
            return true;
        }

        int quantidadePassos = Math.max(1, (int) Math.ceil(distancia / 8.0));

        for (int passo = 1; passo < quantidadePassos; passo++) {
            double proporcao = passo / (double) quantidadePassos;
            double x = origem.getX() + deslocamento.getX() * proporcao;
            double y = origem.getY() + deslocamento.getY() * proporcao;
            if (pontoBloqueiaVisao(x, y)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Mede quanto um raio pode avançar antes de encontrar uma colisão.
     * É usado para o desenho do campo de visão não atravessar obstáculos.
     */
    public double obterAlcanceLivre(
            Point2D origem,
            Point2D direcao,
            double alcanceMaximo
    ) {
        if (blocosComColisao == null || direcao.magnitude() == 0) {
            return 0;
        }

        Point2D direcaoNormalizada = direcao.normalize();
        double tamanhoPasso = 4.0;

        for (double distancia = tamanhoPasso;
             distancia <= alcanceMaximo;
             distancia += tamanhoPasso) {
            Point2D ponto = origem.add(
                    direcaoNormalizada.multiply(distancia)
            );
            if (pontoBloqueiaVisao(ponto.getX(), ponto.getY())) {
                return Math.max(0, distancia - tamanhoPasso);
            }
        }

        return alcanceMaximo;
    }

    /**
     * Encontra um caminho na grade com A*, priorizando os tiles que aproximam
     * o guarda do destino sem permitir que ele atravesse obstáculos.
     */
    public List<Point2D> encontrarCaminho(
            Point2D origem,
            Point2D destino
    ) {
        if (blocosComColisao == null) {
            return List.of(origem);
        }

        int linhaInicialOriginal = (int) (origem.getY() / alturaTileMapa);
        int colunaInicialOriginal = (int) (origem.getX() / larguraTileMapa);
        int linhaFinalOriginal = (int) (destino.getY() / alturaTileMapa);
        int colunaFinalOriginal = (int) (destino.getX() / larguraTileMapa);

        EncontradorCaminhoAStar.Celula celulaInicial =
                encontrarCelulaLivreMaisProxima(
                        linhaInicialOriginal,
                        colunaInicialOriginal,
                        origem
                );
        EncontradorCaminhoAStar.Celula celulaFinal =
                encontrarCelulaLivreMaisProxima(
                        linhaFinalOriginal,
                        colunaFinalOriginal,
                        destino
                );

        /*
         * Objetos menores que um tile podem marcar parcialmente o quadrado
         * onde o guarda ou o jogador estão. Em vez de cancelar a perseguição,
         * começamos/terminamos no quadrado livre mais próximo.
         */
        if (celulaInicial == null || celulaFinal == null) {
            return List.of(origem);
        }

        List<EncontradorCaminhoAStar.Celula> caminhoNaGrade =
                EncontradorCaminhoAStar.encontrar(
                        blocosComColisao,
                        celulaInicial,
                        celulaFinal
                );

        if (caminhoNaGrade.isEmpty()) {
            return List.of(origem);
        }

        List<Point2D> caminho = new ArrayList<>();
        caminho.add(origem);

        // Se a origem precisou ser corrigida, primeiro tira o guarda da
        // célula parcialmente ocupada antes de seguir o trajeto do A*.
        if (linhaInicialOriginal != celulaInicial.linha()
                || colunaInicialOriginal != celulaInicial.coluna()) {
            caminho.add(centroDoTile(
                    celulaInicial.linha(),
                    celulaInicial.coluna()
            ));
        }

        // A primeira célula contém a origem real do guarda. As seguintes são
        // convertidas para o centro dos tiles livres calculados pelo A*.
        for (int indice = 1; indice < caminhoNaGrade.size(); indice++) {
            EncontradorCaminhoAStar.Celula celula = caminhoNaGrade.get(indice);
            caminho.add(centroDoTile(celula.linha(), celula.coluna()));
        }

        boolean destinoOriginalEstaLivre =
                linhaFinalOriginal == celulaFinal.linha()
                        && colunaFinalOriginal == celulaFinal.coluna();
        if (destinoOriginalEstaLivre
                && !caminho.get(caminho.size() - 1).equals(destino)) {
            caminho.add(destino);
        }

        return caminho;
    }

    /** Localiza rapidamente a célula livre mais próxima de uma posição. */
    private EncontradorCaminhoAStar.Celula encontrarCelulaLivreMaisProxima(
            int linhaInicial,
            int colunaInicial,
            Point2D referencia
    ) {
        if (blocosComColisao == null || blocosComColisao.length == 0) {
            return null;
        }

        int linhas = blocosComColisao.length;
        int colunas = blocosComColisao[0].length;
        int linhaLimitada = Math.max(0, Math.min(linhas - 1, linhaInicial));
        int colunaLimitada = Math.max(
                0,
                Math.min(colunas - 1, colunaInicial)
        );

        if (!blocosComColisao[linhaLimitada][colunaLimitada]) {
            return new EncontradorCaminhoAStar.Celula(
                    linhaLimitada,
                    colunaLimitada
            );
        }

        int maiorRaio = linhas + colunas;
        for (int raio = 1; raio <= maiorRaio; raio++) {
            EncontradorCaminhoAStar.Celula melhor = null;
            double menorDistancia = Double.MAX_VALUE;

            int primeiraLinha = Math.max(0, linhaLimitada - raio);
            int ultimaLinha = Math.min(linhas - 1, linhaLimitada + raio);
            int primeiraColuna = Math.max(0, colunaLimitada - raio);
            int ultimaColuna = Math.min(colunas - 1, colunaLimitada + raio);

            for (int linha = primeiraLinha; linha <= ultimaLinha; linha++) {
                for (int coluna = primeiraColuna;
                     coluna <= ultimaColuna;
                     coluna++) {
                    int distanciaNaGrade = Math.abs(linha - linhaLimitada)
                            + Math.abs(coluna - colunaLimitada);
                    if (distanciaNaGrade != raio
                            || blocosComColisao[linha][coluna]) {
                        continue;
                    }

                    Point2D centro = centroDoTile(linha, coluna);
                    double distancia = centro.distance(referencia);
                    if (distancia < menorDistancia) {
                        menorDistancia = distancia;
                        melhor = new EncontradorCaminhoAStar.Celula(
                                linha,
                                coluna
                        );
                    }
                }
            }

            if (melhor != null) {
                return melhor;
            }
        }

        return null;
    }

    /**
     * Escolhe posições livres e diferentes ao redor do jogador.
     * Assim os guardas cercam o alvo em vez de ocuparem o mesmo ponto.
     */
    public List<Point2D> encontrarPosicoesAoRedor(
            Point2D centro,
            int quantidade
    ) {
        int linhaCentral = (int) (centro.getY() / alturaTileMapa);
        int colunaCentral = (int) (centro.getX() / larguraTileMapa);

        EncontradorCaminhoAStar.Celula celulaCentral =
                encontrarCelulaLivreMaisProxima(
                        linhaCentral,
                        colunaCentral,
                        centro
                );
        if (celulaCentral == null) {
            return List.of(centro);
        }
        linhaCentral = celulaCentral.linha();
        colunaCentral = celulaCentral.coluna();

        int quantidadeLinhas = blocosComColisao.length;
        int quantidadeColunas = blocosComColisao[0].length;
        int[][] distancia = new int[quantidadeLinhas][quantidadeColunas];

        for (int linha = 0; linha < quantidadeLinhas; linha++) {
            java.util.Arrays.fill(distancia[linha], -1);
        }

        ArrayDeque<int[]> fila = new ArrayDeque<>();
        List<Point2D> candidatas = new ArrayList<>();
        fila.add(new int[]{linhaCentral, colunaCentral});
        distancia[linhaCentral][colunaCentral] = 0;

        int[][] direcoes = {
                {-1, 0}, {0, 1}, {1, 0}, {0, -1}
        };

        while (!fila.isEmpty()) {
            int[] atual = fila.removeFirst();
            int distanciaAtual = distancia[atual[0]][atual[1]];

            if (distanciaAtual >= 2 && distanciaAtual <= 8) {
                candidatas.add(centroDoTile(atual[0], atual[1]));
            }

            if (distanciaAtual >= 8) {
                continue;
            }

            for (int[] direcao : direcoes) {
                int proximaLinha = atual[0] + direcao[0];
                int proximaColuna = atual[1] + direcao[1];

                if (posicaoDaGradeBloqueada(proximaLinha, proximaColuna)
                        || distancia[proximaLinha][proximaColuna] != -1) {
                    continue;
                }

                distancia[proximaLinha][proximaColuna] = distanciaAtual + 1;
                fila.addLast(new int[]{proximaLinha, proximaColuna});
            }
        }

        if (candidatas.isEmpty()) {
            return List.of(centroDoTile(linhaCentral, colunaCentral));
        }

        // Ordena em volta do jogador para distribuir os guardas pelos lados.
        candidatas.sort((primeira, segunda) -> Double.compare(
                Math.atan2(
                        primeira.getY() - centro.getY(),
                        primeira.getX() - centro.getX()
                ),
                Math.atan2(
                        segunda.getY() - centro.getY(),
                        segunda.getX() - centro.getX()
                )
        ));

        List<Point2D> escolhidas = new ArrayList<>();
        int totalDesejado = Math.min(quantidade, candidatas.size());

        for (int indice = 0; indice < totalDesejado; indice++) {
            int indiceCandidata = (int) Math.floor(
                    indice * candidatas.size() / (double) totalDesejado
            );
            escolhidas.add(candidatas.get(indiceCandidata));
        }

        return escolhidas;
    }

    private Point2D centroDoTile(int linha, int coluna) {
        return new Point2D(
                coluna * larguraTileMapa + larguraTileMapa / 2.0,
                linha * alturaTileMapa + alturaTileMapa / 2.0
        );
    }

    private boolean posicaoDaGradeBloqueada(int linha, int coluna) {
        return linha < 0
                || coluna < 0
                || linha >= blocosComColisao.length
                || coluna >= blocosComColisao[0].length
                || blocosComColisao[linha][coluna];
    }

    /** Verifica a visão na forma real dos objetos da sala de treino. */
    private boolean pontoBloqueiaVisao(double x, double y) {
        if (usaVisaoPrecisaDojo) {
            if (x < 0 || y < 0 || x >= 960 || y >= 640) {
                return true;
            }

            for (Rectangle2D obstaculo : obstaculosDojo) {
                if (obstaculo.contains(x, y)) {
                    return true;
                }
            }

            return false;
        }

        return posicaoDaGradeBloqueada(
                (int) (y / alturaTileMapa),
                (int) (x / larguraTileMapa)
        );
    }

    /**
     * Carrega o arquivo TSX indicado pelo mapa e a imagem PNG
     * que contém todos os tiles.
     */
    private List<DadosTileset> carregarTilesets(
            URL enderecoMapa,
            Element mapa
    ) {
        NodeList elementosTileset = mapa.getElementsByTagName("tileset");

        if (elementosTileset.getLength() == 0) {
            throw new IllegalStateException("O mapa não possui um tileset.");
        }

        List<DadosTileset> tilesets = new ArrayList<>();

        for (int indice = 0; indice < elementosTileset.getLength(); indice++) {
            Element referenciaTileset =
                    (Element) elementosTileset.item(indice);
            int primeiroGid = lerInteiro(referenciaTileset, "firstgid");
            String caminhoTileset =
                    referenciaTileset.getAttribute("source");

            if (caminhoTileset.isBlank()) {
                throw new IllegalStateException(
                        "Tilesets incorporados ao TMX ainda não são suportados."
                );
            }

            URL enderecoTileset =
                    resolverEndereco(enderecoMapa, caminhoTileset);
            Document documentoTileset = lerDocumentoXml(enderecoTileset);
            Element tileset = documentoTileset.getDocumentElement();

            int larguraTile = lerInteiro(tileset, "tilewidth");
            int alturaTile = lerInteiro(tileset, "tileheight");
            int quantidadeColunas = lerInteiro(tileset, "columns");

            if (quantidadeColunas <= 0) {
                throw new IllegalStateException(
                        "O tileset não possui uma grade de imagem: "
                                + caminhoTileset
                );
            }

            NodeList elementosImagem =
                    tileset.getElementsByTagName("image");

            if (elementosImagem.getLength() == 0) {
                throw new IllegalStateException(
                        "O tileset não possui uma imagem: " + caminhoTileset
                );
            }

            Element elementoImagem = (Element) elementosImagem.item(0);
            URL enderecoImagem = resolverEndereco(
                    enderecoTileset,
                    elementoImagem.getAttribute("source")
            );
            Image imagem = new Image(enderecoImagem.toExternalForm(), false);

            if (imagem.isError()) {
                throw new IllegalStateException(
                        "Não foi possível carregar a imagem do tileset: "
                                + enderecoImagem,
                        imagem.getException()
                );
            }

            tilesets.add(new DadosTileset(
                    imagem,
                    primeiroGid,
                    quantidadeColunas,
                    larguraTile,
                    alturaTile
            ));
        }

        return List.copyOf(tilesets);
    }

    /**
     * Localiza o tileset responsável por um GID do mapa.
     */
    private DadosTileset encontrarTileset(
            List<DadosTileset> tilesets,
            int gid
    ) {
        DadosTileset encontrado = null;

        for (DadosTileset candidato : tilesets) {
            if (gid >= candidato.primeiroGid
                    && (encontrado == null
                    || candidato.primeiroGid > encontrado.primeiroGid)) {
                encontrado = candidato;
            }
        }

        return encontrado;
    }

    /**
     * Desenha uma camada que utiliza dados no formato CSV.
     */
    private void desenharCamada(
            GraphicsContext pincel,
            Element camada,
            List<DadosTileset> tilesets,
            int larguraTileMapa,
            int alturaTileMapa,
            boolean camadaTemColisao
    ) {
        int larguraCamada = lerInteiro(camada, "width");
        NodeList elementosData = camada.getElementsByTagName("data");

        if (elementosData.getLength() == 0) {
            return;
        }

        Element data = (Element) elementosData.item(0);

        // Nosso mapa foi salvo no Tiled utilizando codificação CSV.
        if (!"csv".equals(data.getAttribute("encoding"))) {
            throw new IllegalStateException(
                    "A camada " + camada.getAttribute("name")
                            + " não utiliza codificação CSV."
            );
        }

        // Separa a sequência CSV em identificadores individuais de tiles.
        String[] identificadores = data.getTextContent().trim().split(",");

        for (int indice = 0; indice < identificadores.length; indice++) {
            String textoGid = identificadores[indice].trim();

            // Ignora espaços vazios que possam existir no final do CSV.
            if (textoGid.isEmpty()) {
                continue;
            }

            /*
             * O Tiled reserva alguns bits do GID para indicar rotações.
             * A máscara mantém somente o número real do tile.
             */
            long gidCompleto = Long.parseLong(textoGid);
            int gid = (int) (gidCompleto & 0x0FFFFFFFL);

            // GID zero representa uma posição vazia na camada.
            if (gid == 0) {
                continue;
            }

            // Descobre a posição ocupada pelo tile dentro do mapa.
            int colunaMapa = indice % larguraCamada;
            int linhaMapa = indice / larguraCamada;

            /*
             * Registra a colisão antes de desenhar o tile.
             * Na camada de vegetação, somente o GID 116 representa
             * árvore; os outros GIDs são partes do chão e do caminho.
             */
            if (camadaTemColisao && tileBloqueiaMovimento(camada, gid)) {
                blocosComColisao[linhaMapa][colunaMapa] = true;
            }

            DadosTileset tileset = encontrarTileset(tilesets, gid);

            if (tileset == null) {
                throw new IllegalStateException(
                        "Nenhum tileset foi encontrado para o GID " + gid
                );
            }

            // Converte o GID do mapa para o índice interno do tileset.
            int indiceNoTileset = gid - tileset.primeiroGid;

            // Descobre onde o tile está localizado dentro da imagem PNG.
            int origemX = (indiceNoTileset % tileset.quantidadeColunas)
                    * tileset.larguraTile;
            int origemY = (indiceNoTileset / tileset.quantidadeColunas)
                    * tileset.alturaTile;

            // Descobre onde o tile deve aparecer dentro do mapa.
            int destinoX = colunaMapa * larguraTileMapa;
            int destinoY = linhaMapa * alturaTileMapa;

            // Recorta o tile correto da imagem e o desenha no Canvas.
            pincel.drawImage(
                    tileset.imagem,
                    origemX,
                    origemY,
                    tileset.larguraTile,
                    tileset.alturaTile,
                    destinoX,
                    destinoY,
                    larguraTileMapa,
                    alturaTileMapa
            );
        }
    }

    /**
     * Verifica se uma camada recebeu a propriedade personalizada
     * colisao=true dentro do Tiled.
     */
    private boolean camadaPossuiColisao(Element camada) {
        NodeList propriedades = camada.getElementsByTagName("property");

        for (int indice = 0; indice < propriedades.getLength(); indice++) {
            Element propriedade = (Element) propriedades.item(indice);

            if (!"colisao".equalsIgnoreCase(
                    propriedade.getAttribute("name")
            )) {
                continue;
            }

            String valor = propriedade.getAttribute("value");

            // O Tiled também pode armazenar o valor dentro do elemento.
            if (valor.isBlank()) {
                valor = propriedade.getTextContent();
            }

            return Boolean.parseBoolean(valor.trim());
        }

        return false;
    }

    /**
     * Decide quais tiles de uma camada bloqueiam o movimento.
     */
    private boolean tileBloqueiaMovimento(Element camada, int gid) {
        String nomeCamada = camada.getAttribute("name");

        /*
         * A camada ParedesVegetacao contém alguns tiles de acabamento
         * do chão. Por isso, apenas o tile da árvore deve bloquear.
         */
        if ("ParedesVegetacao".equalsIgnoreCase(nomeCamada)) {
            return gid == GID_ARVORE;
        }

        // Nas outras camadas de colisão, qualquer tile ocupado bloqueia.
        return true;
    }

    /**
     * Verifica se o retângulo do jogador encosta em algum tile bloqueado.
     *
     * @param x posição horizontal desejada.
     * @param y posição vertical desejada.
     * @param largura largura do jogador.
     * @param altura altura do jogador.
     * @return true quando a posição está bloqueada.
     */
    public boolean possuiColisao(
            double x,
            double y,
            double largura,
            double altura
    ) {
        if (leitorColisaoDojo != null) {
            Rectangle2D corpo = new Rectangle2D(x, y, largura, altura);
            for (Rectangle2D obstaculo : obstaculosDojo) {
                if (obstaculo.intersects(corpo)) {
                    return true;
                }
            }
            return possuiColisaoNaArteDojo(x, y, largura, altura);
        }

        if (!obstaculosDojo.isEmpty()) {
            double margemDojo = Math.min(5.0,
                    Math.min(largura, altura) / 4.0);
            Rectangle2D corpo = new Rectangle2D(
                    x + margemDojo,
                    y + margemDojo,
                    largura - margemDojo * 2,
                    altura - margemDojo * 2
            );

            for (Rectangle2D obstaculo : obstaculosDojo) {
                if (obstaculo.intersects(corpo)) {
                    return true;
                }
            }

            return false;
        }

        if (blocosComColisao == null) {
            return false;
        }

        /*
         * A margem deixa a caixa de colisão um pouco menor que o desenho
         * do jogador, tornando a passagem pelos corredores mais natural.
         */
        double margem = Math.min(
                5.0,
                Math.min(largura, altura) / 4.0
        );

        int colunaEsquerda = (int) ((x + margem) / larguraTileMapa);
        int colunaDireita = (int) (
                (x + largura - margem - 0.001) / larguraTileMapa
        );
        int linhaSuperior = (int) ((y + margem) / alturaTileMapa);
        int linhaInferior = (int) (
                (y + altura - margem - 0.001) / alturaTileMapa
        );

        // Sair da grade também é considerado uma colisão.
        if (linhaSuperior < 0
                || colunaEsquerda < 0
                || linhaInferior >= blocosComColisao.length
                || colunaDireita >= blocosComColisao[0].length) {
            return true;
        }

        // Examina todos os blocos tocados pela caixa do jogador.
        for (int linha = linhaSuperior; linha <= linhaInferior; linha++) {
            for (int coluna = colunaEsquerda;
                 coluna <= colunaDireita;
                 coluna++) {

                if (blocosComColisao[linha][coluna]) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Usa a própria arte do dojo como referência de colisão. O piso claro
     * permanece livre; paredes, móveis e objetos escuros bloqueiam.
     */
    private boolean possuiColisaoNaArteDojo(
            double x, double y, double largura, double altura
    ) {
        if (x < 0 || y < 0 || x + largura > 960 || y + altura > 640) {
            return true;
        }

        // A escadaria da entrada é a única abertura da parede inferior.
        boolean naEntrada = x >= 278 && x + largura <= 354
                && y >= 560;
        if (naEntrada) {
            return false;
        }

        // Porta shoji no alto esquerdo: é a passagem para a sala de treino.
        // O desenho continua visível, mas o corredor central da porta fica
        // livre para o jogador alcançar o ponto de transição.
        boolean naEntradaSalaTreino = x >= 72 && x + largura <= 202
                && y >= 54 && y + altura <= 176;
        if (naEntradaSalaTreino) {
            return false;
        }

        // Porta larga entre o salão principal e a ala direita.
        boolean naPassagemLarga = x >= 500 && x + largura <= 680
                && y >= 210 && y + altura <= 365;
        if (naPassagemLarga) {
            return false;
        }

        // Esta porta leva à sala já desenhada no canto inferior direito.
        boolean naEntradaSalaLateral = x >= 700 && x + largura <= 845
                && y >= 318 && y + altura <= 500;
        if (naEntradaSalaLateral) {
            return false;
        }

        int amostrasEscuras = 0;
        int totalAmostras = 0;

        for (int linha = 1; linha <= 6; linha++) {
            for (int coluna = 1; coluna <= 6; coluna++) {
                double pontoX = x + largura * coluna / 7.0;
                double pontoY = y + altura * linha / 7.0;
                int imagemX = Math.min(larguraArteDojo - 1,
                        (int) (pontoX * larguraArteDojo / 960.0));
                int imagemY = Math.min(alturaArteDojo - 1,
                        (int) (pontoY * alturaArteDojo / 640.0));
                Color cor = leitorColisaoDojo.getColor(imagemX, imagemY);

                if (cor.getBrightness() < 0.34) {
                    amostrasEscuras++;
                }
                totalAmostras++;
            }
        }

        // Linhas finas entre tatames não bloqueiam; objetos ocupam várias
        // amostras escuras dentro do corpo do personagem.
        return amostrasEscuras >= totalAmostras / 4;
    }

    /**
     * Abre e interpreta um arquivo XML localizado por uma URL.
     */
    private Document lerDocumentoXml(URL enderecoArquivo) {
        try (InputStream fluxo = enderecoArquivo.openStream()) {
            DocumentBuilderFactory fabrica =
                    DocumentBuilderFactory.newInstance();

            // Evita que o XML tente carregar entidades externas.
            fabrica.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );
            fabrica.setExpandEntityReferences(false);

            return fabrica.newDocumentBuilder().parse(fluxo);

        } catch (Exception erro) {
            throw new IllegalStateException(
                    "Não foi possível interpretar o XML: " + enderecoArquivo,
                    erro
            );
        }
    }

    /**
     * Converte um atributo numérico de um elemento XML para inteiro.
     */
    private int lerInteiro(Element elemento, String atributo) {
        String valor = elemento.getAttribute(atributo);

        if (valor.isBlank()) {
            throw new IllegalStateException(
                    "O atributo obrigatório '" + atributo + "' não foi encontrado."
            );
        }

        return Integer.parseInt(valor);
    }

    /**
     * Resolve caminhos relativos utilizados pelos arquivos TMX e TSX.
     */
    private URL resolverEndereco(URL enderecoBase, String caminhoRelativo) {
        try {
            return new URL(enderecoBase, caminhoRelativo);
        } catch (Exception erro) {
            throw new IllegalStateException(
                    "Não foi possível resolver o caminho: " + caminhoRelativo,
                    erro
            );
        }
    }

    /**
     * Objeto retangular criado na camada ItensInterativos do Tiled.
     */
    public record ObjetoInterativo(
            String nome,
            double x,
            double y,
            double largura,
            double altura
    ) {
        public Point2D centro() {
            return new Point2D(
                    x + largura / 2.0,
                    y + altura / 2.0
            );
        }
    }

    /**
     * Agrupa as informações necessárias para recortar os tiles da imagem.
     */
    private static class DadosTileset {
        private final Image imagem;
        private final int primeiroGid;
        private final int quantidadeColunas;
        private final int larguraTile;
        private final int alturaTile;

        private DadosTileset(
                Image imagem,
                int primeiroGid,
                int quantidadeColunas,
                int larguraTile,
                int alturaTile
        ) {
            this.imagem = imagem;
            this.primeiroGid = primeiroGid;
            this.quantidadeColunas = quantidadeColunas;
            this.larguraTile = larguraTile;
            this.alturaTile = alturaTile;
        }
    }
}
