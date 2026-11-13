package io.github.p3pandroid;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The launcher's texts in the device's language. Callers keep their Spanish and English pair (t(es, en)); the other
 * languages are looked up here by the English text, which falls back to English when a text has no translation.
 * Texts with a variable part take it through String.format (%s, %d), so the key stays the same.
 * Only the launcher: the game picks its own text language (the "language" key of config.txt, or the device's).
 */
final class Lang {
    // The columns of the table, after the English key.
    private static final String[] COLUMNS = {"fr", "de", "it", "pt", "ja", "ko", "zh-Hans", "zh-Hant"};
    private static final Map<String, String[]> TABLE = new HashMap<>();
    private static final String DEVICE = device();

    private Lang() {}

    /** The device's language as one of es, en and the table's columns (anything else: en). */
    static String device() {
        Locale l = Locale.getDefault();
        String lang = l.getLanguage();
        if (lang.equals("zh")) {
            String script = l.getScript(), country = l.getCountry();
            boolean traditional = script.equals("Hant")
                    || (script.isEmpty() && (country.equals("TW") || country.equals("HK") || country.equals("MO")));
            return traditional ? "zh-Hant" : "zh-Hans";
        }
        if (lang.equals("es") || lang.equals("en")) return lang;
        for (String c : COLUMNS)
            if (c.equals(lang)) return c;
        return "en";
    }

    static String t(String spanish, String english) {
        if (DEVICE.equals("es")) return spanish;
        if (DEVICE.equals("en")) return english;
        String[] row = TABLE.get(english);
        if (row == null) return english;
        for (int k = 0; k < COLUMNS.length; k++)
            if (COLUMNS[k].equals(DEVICE)) return row[k];
        return english;
    }

    static String f(String spanish, String english, Object... args) {
        return String.format(Locale.getDefault(), t(spanish, english), args);
    }

    private static void put(String english, String... others) {
        if (others.length != COLUMNS.length) throw new IllegalArgumentException(english);
        TABLE.put(english, others);
    }

    static {
        // Options
        // Added with the 0.3.0 options screen and the texts that were missing
        put("Tools", "Outils", "Werkzeuge", "Strumenti", "Ferramentas", "ツール", "도구", "工具", "工具");
        put("720p (recommended)",
                "720p (recommandée)",
                "720p (empfohlen)",
                "720p (consigliata)",
                "720p (recomendada)",
                "720p（推奨）",
                "720p (권장)",
                "720p（推荐）",
                "720p（建議）");
        put("1080p (heavy)",
                "1080p (lourde)",
                "1080p (rechenintensiv)",
                "1080p (pesante)",
                "1080p (pesada)",
                "1080p（高負荷）",
                "1080p (무거움)",
                "1080p（负载高）",
                "1080p（負載高）");
        put("720p is the handheld console's resolution and the one that runs best; 1080p needs a powerful phone.",
                "720p est la résolution de la console portable et celle qui tourne le mieux ; 1080p demande un téléphone puissant.",
                "720p ist die Auflösung der Handheld-Konsole und läuft am besten; 1080p braucht ein leistungsstarkes Handy.",
                "720p è la risoluzione della console portatile e quella che funziona meglio; 1080p richiede un telefono potente.",
                "720p é a resolução do console portátil e a que roda melhor; 1080p exige um celular potente.",
                "720pは携帯モードの解像度で、最も快適に動作します。1080pは高性能なスマートフォンが必要です。",
                "720p는 휴대 모드 해상도이며 가장 잘 동작합니다. 1080p는 고성능 휴대폰이 필요합니다.",
                "720p 是掌机模式的分辨率，运行最流畅；1080p 需要高性能手机。",
                "720p 是掌機模式的解析度，執行最流暢；1080p 需要高效能手機。");
        put("Sharp upscaling (FSR)",
                "Mise à l'échelle nette (FSR)",
                "Scharfe Hochskalierung (FSR)",
                "Upscaling nitido (FSR)",
                "Ampliação nítida (FSR)",
                "シャープなアップスケール（FSR）",
                "선명한 업스케일링 (FSR)",
                "锐利放大（FSR）",
                "銳利放大（FSR）");
        put("Performance",
                "Performances",
                "Leistung",
                "Prestazioni",
                "Desempenho",
                "パフォーマンス",
                "성능",
                "性能",
                "效能");
        put("On modest phones 30 fps is usually smoother than 60. \"Prefer the fast ones\" helps when the GPU is not at its limit.",
                "Sur les téléphones modestes, 30 fps est souvent plus fluide que 60. \"Privilégier les rapides\" aide quand le GPU n'est pas à sa limite.",
                "Auf einfachen Handys läuft 30 fps meist flüssiger als 60. \"Schnelle bevorzugen\" hilft, wenn die GPU nicht am Limit ist.",
                "Sui telefoni modesti 30 fps è di solito più fluido di 60. \"Preferisci i veloci\" aiuta quando la GPU non è al limite.",
                "Em celulares modestos, 30 fps costuma ser mais fluido que 60. \"Priorizar os rápidos\" ajuda quando a GPU não está no limite.",
                "性能の低いスマートフォンでは、60fpsより30fpsの方が滑らかなことが多いです。GPUが限界に達していない場合は「高速コアを優先」が有効です。",
                "성능이 낮은 휴대폰에서는 60fps보다 30fps가 더 부드러운 경우가 많습니다. GPU가 한계에 도달하지 않았다면 \"빠른 코어 우선\"이 도움이 됩니다.",
                "在性能较低的手机上，30 fps 通常比 60 fps 更流畅。当 GPU 未达到极限时，“优先使用高速核心”会有帮助。",
                "在效能較低的手機上，30 fps 通常比 60 fps 更流暢。當 GPU 未達極限時，「優先使用高速核心」會有幫助。");
        put("CPU cores",
                "Cœurs du processeur",
                "CPU-Kerne",
                "Core della CPU",
                "Núcleos da CPU",
                "CPUコア",
                "CPU 코어",
                "CPU 核心",
                "CPU 核心");
        put("Automatic",
                "Automatique",
                "Automatisch",
                "Automatico",
                "Automático",
                "自動",
                "자동",
                "自动",
                "自動");
        put("Prefer the fast ones",
                "Privilégier les rapides",
                "Schnelle bevorzugen",
                "Preferisci i veloci",
                "Priorizar os rápidos",
                "高速コアを優先",
                "빠른 코어 우선",
                "优先使用高速核心",
                "優先使用高速核心");
        put("Audio",
                "Audio",
                "Audio",
                "Audio",
                "Áudio",
                "オーディオ",
                "오디오",
                "音频",
                "音訊");
        put("Audio output",
                "Sortie audio",
                "Audioausgabe",
                "Uscita audio",
                "Saída de áudio",
                "オーディオ出力",
                "오디오 출력",
                "音频输出",
                "音訊輸出");
        put("Low latency",
                "Faible latence",
                "Niedrige Latenz",
                "Bassa latenza",
                "Baixa latência",
                "低遅延",
                "저지연",
                "低延迟",
                "低延遲");
        put("Normal (more stable)",
                "Normal (plus stable)",
                "Normal (stabiler)",
                "Normale (più stabile)",
                "Normal (mais estável)",
                "標準（より安定）",
                "일반 (더 안정적)",
                "标准（更稳定）",
                "標準（更穩定）");
        put("If the sound breaks up with headphones, try \"Normal\".",
                "Si le son saccade avec un casque, essayez \"Normal\".",
                "Wenn der Ton mit Kopfhörern aussetzt, probiere \"Normal\".",
                "Se l'audio si interrompe con le cuffie, prova \"Normale\".",
                "Se o som falhar com fone de ouvido, tente \"Normal\".",
                "ヘッドホンで音が途切れる場合は「標準」を試してください。",
                "헤드폰에서 소리가 끊기면 \"일반\"을 사용해 보세요.",
                "如果耳机出现声音断续，请尝试“标准”。",
                "如果耳機出現聲音斷續，請嘗試「標準」。");
        put("Mods",
                "Mods",
                "Mods",
                "Mod",
                "Mods",
                "Mod",
                "모드",
                "模组",
                "模組");
        put("Mod manager",
                "Gestionnaire de mods",
                "Mod-Manager",
                "Gestione mod",
                "Gerenciador de mods",
                "Modマネージャー",
                "모드 관리자",
                "模组管理器",
                "模組管理器");
        put("Open",
                "Ouvrir",
                "Öffnen",
                "Apri",
                "Abrir",
                "開く",
                "열기",
                "打开",
                "開啟");
        put("Device diagnostics",
                "Diagnostic de l'appareil",
                "Gerätediagnose",
                "Diagnostica del dispositivo",
                "Diagnóstico do dispositivo",
                "端末の診断",
                "기기 진단",
                "设备诊断",
                "裝置診斷");
        put("Game: %s. Prepare the local shader cache to continue.",
                "Jeu : %s. Préparez le cache de shaders local pour continuer.",
                "Spiel: %s. Bereite den lokalen Shader-Cache vor, um fortzufahren.",
                "Gioco: %s. Prepara la cache degli shader locale per continuare.",
                "Jogo: %s. Prepare o cache de shaders local para continuar.",
                "ゲーム：%s。続行するにはローカルのシェーダーキャッシュを準備してください。",
                "게임: %s. 계속하려면 로컬 셰이더 캐시를 준비하세요.",
                "游戏：%s。请先准备本地着色器缓存以继续。",
                "遊戲：%s。請先準備本機著色器快取以繼續。");
        put("Scanning game modules (%d of %d)…",
                "Analyse des modules du jeu (%d sur %d)…",
                "Spielmodule werden durchsucht (%d von %d)…",
                "Scansione dei moduli del gioco (%d di %d)…",
                "Analisando módulos do jogo (%d de %d)…",
                "ゲームモジュールをスキャン中（%d / %d）…",
                "게임 모듈 검사 중 (%d/%d)…",
                "正在扫描游戏模块（%d / %d）…",
                "正在掃描遊戲模組（%d / %d）…");
        put("Generating local shaders (module %d of %d)…",
                "Génération des shaders locaux (module %d sur %d)…",
                "Lokale Shader werden erzeugt (Modul %d von %d)…",
                "Generazione degli shader locali (modulo %d di %d)…",
                "Gerando shaders locais (módulo %d de %d)…",
                "ローカルシェーダーを生成中（モジュール %d / %d）…",
                "로컬 셰이더 생성 중 (모듈 %d/%d)…",
                "正在生成本地着色器（模块 %d / %d）…",
                "正在產生本機著色器（模組 %d / %d）…");
        put("Keep the app open during preparation. Additional shaders are generated on first use and may briefly pause gameplay.",
                "Gardez l'application ouverte pendant la préparation. Des shaders supplémentaires sont générés à la première utilisation et peuvent brièvement interrompre le jeu.",
                "Lass die App während der Vorbereitung geöffnet. Weitere Shader werden bei der ersten Verwendung erzeugt und können das Spiel kurz anhalten.",
                "Tieni l'app aperta durante la preparazione. Altri shader vengono generati al primo utilizzo e possono mettere brevemente in pausa il gioco.",
                "Mantenha o app aberto durante a preparação. Shaders adicionais são gerados no primeiro uso e podem pausar o jogo por instantes.",
                "準備中はアプリを開いたままにしてください。追加のシェーダーは初回使用時に生成され、ゲームが一瞬止まることがあります。",
                "준비하는 동안 앱을 열어 두세요. 추가 셰이더는 처음 사용할 때 생성되며 게임이 잠시 멈출 수 있습니다.",
                "准备期间请保持应用打开。额外的着色器会在首次使用时生成，可能会让游戏短暂停顿。",
                "準備期間請保持應用程式開啟。額外的著色器會在首次使用時產生，可能讓遊戲短暫停頓。");
        put("Stick",
                "Stick",
                "Stick",
                "Stick",
                "Analógico",
                "スティック",
                "스틱",
                "摇杆",
                "搖桿");
        put("Game files", "Fichiers du jeu", "Spieldateien", "File del gioco", "Arquivos do jogo", "ゲームファイル", "게임 파일",
                "游戏文件", "遊戲檔案");
        put("Language", "Langue", "Sprache", "Lingua", "Idioma", "言語", "언어", "语言", "語言");
        put("Display", "Affichage", "Anzeige", "Schermo", "Imagem", "表示", "화면", "显示", "顯示");
        put("Controls", "Commandes", "Steuerung", "Controlli", "Controles", "操作", "조작", "控制", "控制");
        put("Text and subtitle language", "Langue des textes et sous-titres", "Sprache für Texte und Untertitel",
                "Lingua di testi e sottotitoli", "Idioma dos textos e legendas", "テキストと字幕の言語", "텍스트 및 자막 언어",
                "文本和字幕语言", "文字與字幕語言");
        put("Automatic (device language)", "Automatique (langue de l'appareil)", "Automatisch (Gerätesprache)",
                "Automatica (lingua del dispositivo)", "Automático (idioma do dispositivo)", "自動（端末の言語）",
                "자동 (기기 언어)", "自动（设备语言）", "自動（裝置語言）");
        put("Resolution", "Résolution", "Auflösung", "Risoluzione", "Resolução", "解像度", "해상도", "分辨率", "解析度");
        put("Interface", "Interface", "Benutzeroberfläche", "Interfaccia", "Interface", "インターフェース", "인터페이스",
                "界面", "介面");
        put("720p (faster)", "720p (plus rapide)", "720p (schneller)", "720p (più veloce)", "720p (mais rápida)",
                "720p（高速）", "720p (더 빠름)", "720p（更快）", "720p（較快）");
        put("1080p (sharp text)", "1080p (texte net)", "1080p (scharfer Text)", "1080p (testo nitido)",
                "1080p (texto nítido)", "1080p（くっきりした文字）", "1080p (선명한 텍스트)", "1080p（文字清晰）",
                "1080p（文字清晰）");
        put("Graphics", "Graphismes", "Grafik", "Grafica", "Gráficos", "グラフィック", "그래픽", "画质", "畫質");
        put("Low", "Bas", "Niedrig", "Bassa", "Baixo", "低", "낮음", "低", "低");
        put("Medium", "Moyens", "Mittel", "Media", "Médio", "中", "중간", "中", "中");
        put("High", "Élevés", "Hoch", "Alta", "Alto", "高", "높음", "高", "高");
        put("Frame rate", "Images par seconde", "Bildrate", "Fotogrammi al secondo", "Quadros por segundo",
                "フレームレート", "프레임 레이트", "帧率", "影格率");
        put("Shadows", "Ombres", "Schatten", "Ombre", "Sombras", "影", "그림자", "阴影", "陰影");
        put("Off", "Désactivé", "Aus", "Disattivato", "Desativado", "オフ", "끔", "关", "關");
        put("Fullscreen (stretch)", "Plein écran (étirer)", "Vollbild (strecken)", "Schermo intero (allarga)", "Tela cheia (esticar)", "\u5168\u753b\u9762\uff08\u5f15\u304d\u4f38\u3070\u3057\uff09", "\uc804\uccb4 \ud654\uba74 (\ub298\uc774\uae30)", "\u5168\u5c4f\uff08\u62c9\u4f38\uff09", "\u5168\u87a2\u5e55\uff08\u62c9\u4f38\uff09");
        put("On", "Activé", "An", "Attivato", "Ativado", "オン", "켬", "开", "開");
        put("FPS counter", "Compteur de FPS", "FPS-Anzeige", "Contatore FPS", "Contador de FPS", "FPS表示", "FPS 표시",
                "帧率显示", "FPS 顯示");
        put("On-screen controls", "Commandes tactiles", "Touch-Steuerung", "Comandi touch", "Controles na tela",
                "タッチ操作", "터치 컨트롤", "触屏按键", "觸控按鍵");
        put("Button size", "Taille des boutons", "Tastengröße", "Dimensione dei pulsanti", "Tamanho dos botões",
                "ボタンの大きさ", "버튼 크기", "按键大小", "按鍵大小");
        put("Small", "Petite", "Klein", "Piccola", "Pequeno", "小", "작게", "小", "小");
        put("Normal", "Normale", "Normal", "Normale", "Normal", "標準", "보통", "标准", "標準");
        put("Large", "Grande", "Groß", "Grande", "Grande", "大", "크게", "大", "大");
        put("Button opacity", "Opacité des boutons", "Deckkraft der Tasten", "Opacità dei pulsanti",
                "Opacidade dos botões", "ボタンの不透明度", "버튼 불투명도", "按键不透明度", "按鍵不透明度");
        put("Controls position", "Position des commandes", "Position der Steuerung", "Posizione dei comandi",
                "Posição dos controles", "操作ボタンの配置", "컨트롤 위치", "按键位置", "按鍵位置");
        put("Move", "Déplacer", "Verschieben", "Sposta", "Mover", "移動", "이동", "移动", "移動");
        put("Saves", "Sauvegardes", "Spielstände", "Salvataggi", "Jogos salvos", "セーブデータ", "저장 데이터", "存档",
                "存檔");
        put("Import", "Importer", "Importieren", "Importa", "Importar", "インポート", "가져오기", "导入", "匯入");
        put("Export", "Exporter", "Exportieren", "Esporta", "Exportar", "エクスポート", "내보내기", "导出", "匯出");
        // Status and the play button
        put("All-files access is needed to read the game data.",
                "L'accès à tous les fichiers est nécessaire pour lire les données du jeu.",
                "Für die Spieldaten wird Zugriff auf alle Dateien benötigt.",
                "Serve l'accesso a tutti i file per leggere i dati del gioco.",
                "É preciso o acesso a todos os arquivos para ler os dados do jogo.",
                "ゲームデータを読み込むには、すべてのファイルへのアクセス許可が必要です。",
                "게임 데이터를 읽으려면 모든 파일 액세스 권한이 필요합니다.",
                "需要“所有文件访问权限”才能读取游戏数据。",
                "需要「所有檔案存取權」才能讀取遊戲資料。");
        put("Grant access", "Autoriser l'accès", "Zugriff erlauben", "Concedi l'accesso", "Permitir acesso",
                "アクセスを許可", "액세스 허용", "授予权限", "授予權限");
        put("No game data found in Internal storage/%s (exefs, extract, spirv) from your own copy of the game.",
                "Aucune donnée de jeu dans Stockage interne/%s (exefs, extract, spirv) issue de votre propre copie du jeu.",
                "Keine Spieldaten in Interner Speicher/%s (exefs, extract, spirv) aus deiner eigenen Kopie des Spiels.",
                "Nessun dato di gioco in Memoria interna/%s (exefs, extract, spirv) dalla tua copia del gioco.",
                "Nenhum dado do jogo em Armazenamento interno/%s (exefs, extract, spirv) da sua própria cópia do jogo.",
                "内部ストレージ/%s に、ご自身のゲームから用意したデータ（exefs、extract、spirv）が見つかりません。",
                "내부 저장소/%s 에서 본인 소유 게임의 데이터(exefs, extract, spirv)를 찾을 수 없습니다.",
                "在 内部存储/%s 中找不到从你自己的游戏副本准备的数据（exefs、extract、spirv）。",
                "在 內部儲存空間/%s 中找不到從你自己的遊戲副本準備的資料（exefs、extract、spirv）。");
        put("Internal storage/%s/exefs/main is not Persona 3 Portable 1.0.0 (build %s). Copy the exefs of the base game, without the update.",
                "Stockage interne/%s/exefs/main n'est pas Persona 3 Portable 1.0.0 (build %s). Copiez l'exefs du jeu de base, sans la mise à jour.",
                "Interner Speicher/%s/exefs/main ist nicht Persona 3 Portable 1.0.0 (Build %s). Kopiere das exefs des Grundspiels, ohne das Update.",
                "Memoria interna/%s/exefs/main non è Persona 3 Portable 1.0.0 (build %s). Copia l'exefs del gioco base, senza l'aggiornamento.",
                "Armazenamento interno/%s/exefs/main não é Persona 3 Portable 1.0.0 (build %s). Copie o exefs do jogo base, sem a atualização.",
                "内部ストレージ/%s/exefs/main は Persona 3 Portable 1.0.0 ではありません（ビルド %s）。アップデートを含まない本体の exefs をコピーしてください。",
                "내부 저장소/%s/exefs/main 은(는) Persona 3 Portable 1.0.0 이 아닙니다(빌드 %s). 업데이트가 없는 기본 게임의 exefs 를 복사하세요.",
                "内部存储/%s/exefs/main 不是 Persona 3 Portable 1.0.0（版本 %s）。请复制不含更新的本体游戏的 exefs。",
                "內部儲存空間/%s/exefs/main 不是 Persona 3 Portable 1.0.0（版本 %s）。請複製不含更新的本體遊戲的 exefs。");
        put("Look again", "Chercher à nouveau", "Erneut suchen", "Cerca di nuovo", "Procurar de novo", "再検索",
                "다시 찾기", "重新查找", "重新尋找");
        put("Data ready: %s", "Données prêtes : %s", "Daten bereit: %s", "Dati pronti: %s", "Dados prontos: %s",
                "データ準備完了：%s", "데이터 준비됨: %s", "数据就绪：%s", "資料就緒：%s");
        put("Play", "Jouer", "Spielen", "Gioca", "Jogar", "プレイ", "플레이", "开始游戏", "開始遊戲");
        // Saves
        put("Import saves", "Importer des sauvegardes", "Spielstände importieren", "Importa salvataggi",
                "Importar jogos salvos", "セーブデータのインポート", "저장 데이터 가져오기", "导入存档", "匯入存檔");
        put("Pick a ZIP file or a folder with Persona 3 Portable saves (the DATA01, DATA02... and SYSTEM folders, as JKSV or "
                        + "Checkpoint export them, or the ZIP the Export button makes). Your current saves are copied "
                        + "aside first.",
                "Choisissez un fichier ZIP ou un dossier avec des sauvegardes de Persona 3 Portable (les dossiers DATA01, "
                        + "DATA02... et SYSTEM, tels que JKSV ou Checkpoint les exportent, ou le ZIP créé par le bouton "
                        + "Exporter). Vos sauvegardes actuelles sont copiées à part avant.",
                "Wähle eine ZIP-Datei oder einen Ordner mit Spielständen von Persona 3 Portable (die Ordner DATA01, "
                        + "DATA02... und SYSTEM, wie JKSV oder Checkpoint sie exportieren, oder die ZIP-Datei der "
                        + "Exportieren-Taste). Deine aktuellen Spielstände werden vorher gesichert.",
                "Scegli un file ZIP o una cartella con salvataggi di Persona 3 Portable (le cartelle DATA01, DATA02... e "
                        + "SYSTEM, come le esportano JKSV o Checkpoint, o lo ZIP creato dal pulsante Esporta). Prima "
                        + "viene fatta una copia dei salvataggi attuali.",
                "Escolha um arquivo ZIP ou uma pasta com jogos salvos de Persona 3 Portable (as pastas DATA01, DATA02... e "
                        + "SYSTEM, como o JKSV ou o Checkpoint exportam, ou o ZIP que o botão Exportar cria). Antes, é "
                        + "feita uma cópia dos seus jogos salvos atuais.",
                "Persona 3 Portable のセーブデータが入った ZIP ファイルまたはフォルダを選んでください（JKSV や Checkpoint "
                        + "が書き出す DATA01、DATA02…と SYSTEM フォルダ、またはエクスポートボタンで作った ZIP）。"
                        + "先に現在のセーブデータのコピーを取ります。",
                "Persona 3 Portable 저장 데이터가 든 ZIP 파일이나 폴더를 선택하세요 (JKSV나 Checkpoint가 내보내는 DATA01, "
                        + "DATA02... 및 SYSTEM 폴더, 또는 내보내기 버튼으로 만든 ZIP). 먼저 현재 저장 데이터를 따로 "
                        + "복사해 둡니다.",
                "请选择包含 Persona 3 Portable 存档的 ZIP 文件或文件夹（JKSV 或 Checkpoint 导出的 DATA01、DATA02… 和 "
                        + "SYSTEM 文件夹，或“导出”按钮生成的 ZIP）。会先备份你当前的存档。",
                "請選擇包含 Persona 3 Portable 存檔的 ZIP 檔案或資料夾（JKSV 或 Checkpoint 匯出的 DATA01、DATA02… 和 "
                        + "SYSTEM 資料夾，或「匯出」按鈕產生的 ZIP）。會先備份你目前的存檔。");
        put("ZIP file", "Fichier ZIP", "ZIP-Datei", "File ZIP", "Arquivo ZIP", "ZIP ファイル", "ZIP 파일", "ZIP 文件",
                "ZIP 檔案");
        put("Folder", "Dossier", "Ordner", "Cartella", "Pasta", "フォルダ", "폴더", "文件夹", "資料夾");
        put("Cancel", "Annuler", "Abbrechen", "Annulla", "Cancelar", "キャンセル", "취소", "取消", "取消");
        put("No saves found", "Aucune sauvegarde trouvée", "Keine Spielstände gefunden", "Nessun salvataggio trovato",
                "Nenhum jogo salvo encontrado", "セーブデータが見つかりません", "저장 데이터를 찾을 수 없음", "未找到存档",
                "找不到存檔");
        put("No Persona 3 Portable saves there: look for the DATA01, DATA02... or SYSTEM folders.",
                "Aucune sauvegarde de Persona 3 Portable ici : cherchez les dossiers DATA01, DATA02... ou SYSTEM.",
                "Dort sind keine Spielstände von Persona 3 Portable: suche die Ordner DATA01, DATA02... oder SYSTEM.",
                "Lì non ci sono salvataggi di Persona 3 Portable: cerca le cartelle DATA01, DATA02... o SYSTEM.",
                "Não há jogos salvos de Persona 3 Portable aí: procure as pastas DATA01, DATA02... ou SYSTEM.",
                "そこには Persona 3 Portable のセーブデータがありません。DATA01、DATA02…または SYSTEM フォルダを探してください。",
                "그곳에는 Persona 3 Portable 저장 데이터가 없습니다. DATA01, DATA02... 또는 SYSTEM 폴더를 찾으세요.",
                "那里没有 Persona 3 Portable 的存档：请找 DATA01、DATA02… 或 SYSTEM 文件夹。",
                "那裡沒有 Persona 3 Portable 的存檔：請找 DATA01、DATA02… 或 SYSTEM 資料夾。");
        put("system settings only", "réglages système uniquement", "nur Systemeinstellungen",
                "solo impostazioni di sistema", "apenas configurações do sistema", "システム設定のみ", "시스템 설정만",
                "仅系统设置", "僅系統設定");
        put("slots %s", "emplacements %s", "Plätze %s", "slot %s", "espaços %s", "スロット %s", "슬롯 %s", "存档位 %s",
                "存檔位 %s");
        put("\n\nYour previous saves are in user/%s.", "\n\nVos sauvegardes précédentes sont dans user/%s.",
                "\n\nDeine bisherigen Spielstände liegen in user/%s.", "\n\nI salvataggi precedenti sono in user/%s.",
                "\n\nSeus jogos salvos anteriores estão em user/%s.", "\n\n以前のセーブデータは user/%s にあります。",
                "\n\n이전 저장 데이터는 user/%s 에 있습니다.", "\n\n你之前的存档在 user/%s。", "\n\n你之前的存檔在 user/%s。");
        put("Saves imported", "Sauvegardes importées", "Spielstände importiert", "Salvataggi importati",
                "Jogos salvos importados", "セーブデータをインポートしました", "저장 데이터를 가져왔습니다", "存档已导入",
                "存檔已匯入");
        put("%d files (%s). Open them from Load Game.", "%d fichiers (%s). Ouvrez-les depuis Charger.",
                "%d Dateien (%s). Öffne sie über Laden.", "%d file (%s). Aprili da Carica partita.",
                "%d arquivos (%s). Abra-os em Carregar jogo.", "%d 個のファイル（%s）。ロードから開いてください。",
                "파일 %d개 (%s). 불러오기에서 여세요.", "%d 个文件（%s）。请从“读取存档”打开。",
                "%d 個檔案（%s）。請從「讀取存檔」開啟。");
        put("Import failed", "Échec de l'importation", "Import fehlgeschlagen", "Importazione non riuscita",
                "Falha na importação", "インポートできませんでした", "가져오기 실패", "导入失败", "匯入失敗");
        put("No saves", "Aucune sauvegarde", "Keine Spielstände", "Nessun salvataggio", "Nenhum jogo salvo",
                "セーブデータなし", "저장 데이터 없음", "没有存档", "沒有存檔");
        put("There are no saves yet.", "Il n'y a pas encore de sauvegarde.", "Es gibt noch keine Spielstände.",
                "Non ci sono ancora salvataggi.", "Ainda não há jogos salvos.", "まだセーブデータがありません。",
                "아직 저장 데이터가 없습니다.", "还没有存档。", "還沒有存檔。");
        put("Saves exported", "Sauvegardes exportées", "Spielstände exportiert", "Salvataggi esportati",
                "Jogos salvos exportados", "セーブデータをエクスポートしました", "저장 데이터를 내보냈습니다", "存档已导出",
                "存檔已匯出");
        put("Saved to Download/%s. The Import button takes it back as is.",
                "Enregistré dans Download/%s. Le bouton Importer le reprend tel quel.",
                "Gespeichert in Download/%s. Die Importieren-Taste übernimmt die Datei unverändert.",
                "Salvato in Download/%s. Il pulsante Importa lo riprende così com'è.",
                "Salvo em Download/%s. O botão Importar o aceita como está.",
                "Download/%s に保存しました。インポートボタンでそのまま読み込めます。",
                "Download/%s 에 저장했습니다. 가져오기 버튼으로 그대로 불러올 수 있습니다.",
                "已保存到 Download/%s。可用“导入”按钮原样导入。",
                "已儲存到 Download/%s。可用「匯入」按鈕原樣匯入。");
        put("Export failed", "Échec de l'exportation", "Export fehlgeschlagen", "Esportazione non riuscita",
                "Falha na exportação", "エクスポートできませんでした", "내보내기 실패", "导出失败", "匯出失敗");
        // The controls editor
        put("Drag the controls where you want them.\nThe d-pad and A B X Y move together. Red: they touch.",
                "Faites glisser les commandes où vous voulez.\nLa croix et A B X Y bougent ensemble. En rouge : elles se touchent.",
                "Ziehe die Tasten an die gewünschte Stelle.\nSteuerkreuz und A B X Y bewegen sich gemeinsam. Rot: sie berühren sich.",
                "Trascina i comandi dove vuoi.\nLa croce direzionale e A B X Y si muovono insieme. In rosso: si toccano.",
                "Arraste os controles para onde quiser.\nO direcional e A B X Y se movem juntos. Em vermelho: estão se tocando.",
                "操作ボタンを好きな位置へドラッグしてください。\n十字ボタンと A B X Y は一緒に動きます。赤：重なっています。",
                "컨트롤을 원하는 곳으로 끌어 놓으세요.\n방향 버튼과 A B X Y는 함께 움직입니다. 빨간색: 서로 닿음.",
                "把按键拖到你想要的位置。\n十字键和 A B X Y 会一起移动。红色：互相重叠。",
                "把按鍵拖到你想要的位置。\n十字鍵和 A B X Y 會一起移動。紅色：互相重疊。");
        put("Reset", "Réinitialiser", "Zurücksetzen", "Ripristina", "Restaurar", "リセット", "초기화", "重置", "重設");
        put("Save", "Enregistrer", "Speichern", "Salva", "Salvar", "保存", "저장", "保存", "儲存");
        put("Controls saved", "Commandes enregistrées", "Steuerung gespeichert", "Comandi salvati", "Controles salvos",
                "保存しました", "저장했습니다", "已保存", "已儲存");
        put("Could not write config.txt", "Impossible d'écrire config.txt", "config.txt konnte nicht geschrieben werden",
                "Impossibile scrivere config.txt", "Não foi possível gravar config.txt", "config.txt を書き込めませんでした",
                "config.txt 를 쓸 수 없습니다", "无法写入 config.txt", "無法寫入 config.txt");
        // The game file, its preparation and the icon
        put("Game file (.nsp / .xci)", "Fichier du jeu (.nsp / .xci)", "Spieldatei (.nsp / .xci)", "File del gioco (.nsp / .xci)", "Arquivo do jogo (.nsp / .xci)", "ゲームファイル (.nsp / .xci)", "게임 파일 (.nsp / .xci)", "游戏文件 (.nsp / .xci)", "遊戲檔案 (.nsp / .xci)");
        put("Choose", "Choisir", "Auswählen", "Scegli", "Escolher", "選択", "선택", "选择", "選擇");
        put("Keys (prod.keys)", "Clés (prod.keys)", "Schlüssel (prod.keys)", "Chiavi (prod.keys)", "Chaves (prod.keys)", "キー (prod.keys)", "키 (prod.keys)", "密钥 (prod.keys)", "金鑰 (prod.keys)");
        put("Icon", "Icône", "Symbol", "Icona", "Ícone", "アイコン", "아이콘", "图标", "圖示");
        put("Choose a picture", "Choisir une image", "Bild auswählen", "Scegli un'immagine", "Escolher uma imagem", "画像を選択", "이미지 선택", "选择图片", "選擇圖片");
        put("Choose your game file (a Persona 3 Portable 1.0.0 .nsp or .xci) and your prod.keys.", "Choisissez le fichier de votre jeu (un .nsp ou .xci de Persona 3 Portable 1.0.0) et votre prod.keys.", "Wähle deine Spieldatei (eine .nsp oder .xci von Persona 3 Portable 1.0.0) und deine prod.keys.", "Scegli il file del tuo gioco (un .nsp o .xci di Persona 3 Portable 1.0.0) e il tuo prod.keys.", "Escolha o arquivo do seu jogo (um .nsp ou .xci de Persona 3 Portable 1.0.0) e o seu prod.keys.", "ゲームファイル（Persona 3 Portable 1.0.0 の .nsp または .xci）と prod.keys を選んでください。", "게임 파일(Persona 3 Portable 1.0.0 의 .nsp 또는 .xci)과 prod.keys 를 선택하세요.", "请选择你的游戏文件（Persona 3 Portable 1.0.0 的 .nsp 或 .xci）和 prod.keys。", "請選擇你的遊戲檔案（Persona 3 Portable 1.0.0 的 .nsp 或 .xci）和 prod.keys。");
        put("Choose the game", "Choisir le jeu", "Spiel auswählen", "Scegli il gioco", "Escolher o jogo", "ゲームを選択", "게임 선택", "选择游戏", "選擇遊戲");
        put("The game file can no longer be read (%s): choose it again.", "Le fichier du jeu ne peut plus être lu (%s) : choisissez-le à nouveau.", "Die Spieldatei kann nicht mehr gelesen werden (%s): wähle sie erneut aus.", "Il file del gioco non è più leggibile (%s): sceglilo di nuovo.", "O arquivo do jogo não pode mais ser lido (%s): escolha-o de novo.", "ゲームファイル (%s) を読み込めなくなりました。もう一度選んでください。", "게임 파일(%s)을 더 이상 읽을 수 없습니다. 다시 선택하세요.", "无法再读取游戏文件（%s）：请重新选择。", "無法再讀取遊戲檔案（%s）：請重新選擇。");
        put("Your prod.keys file is missing.", "Il manque votre fichier prod.keys.", "Deine prod.keys-Datei fehlt.", "Manca il tuo file prod.keys.", "Falta o seu arquivo prod.keys.", "prod.keys ファイルがありません。", "prod.keys 파일이 없습니다.", "缺少你的 prod.keys 文件。", "缺少你的 prod.keys 檔案。");
        put("Choose prod.keys", "Choisir prod.keys", "prod.keys auswählen", "Scegli prod.keys", "Escolher prod.keys", "prod.keys を選択", "prod.keys 선택", "选择 prod.keys", "選擇 prod.keys");
        put("Game: %s. It needs preparing: once, a few minutes.", "Jeu : %s. Il doit être préparé : une seule fois, quelques minutes.", "Spiel: %s. Es muss vorbereitet werden: einmalig, einige Minuten.", "Gioco: %s. Va preparato: una sola volta, qualche minuto.", "Jogo: %s. Precisa ser preparado: só uma vez, alguns minutos.", "ゲーム：%s。準備が必要です（最初の一度だけ、数分）。", "게임: %s. 준비가 필요합니다(한 번만, 몇 분).", "游戏：%s。需要准备：仅一次，几分钟。", "遊戲：%s。需要準備：僅一次，幾分鐘。");
        put("Prepare the game", "Préparer le jeu", "Spiel vorbereiten", "Prepara il gioco", "Preparar o jogo", "ゲームを準備", "게임 준비", "准备游戏", "準備遊戲");
        put("Game ready: %s", "Jeu prêt : %s", "Spiel bereit: %s", "Gioco pronto: %s", "Jogo pronto: %s", "ゲームの準備完了：%s", "게임 준비됨: %s", "游戏就绪：%s", "遊戲就緒：%s");
        put("Not a game file", "Ce n'est pas un fichier de jeu", "Keine Spieldatei", "Non è un file di gioco", "Não é um arquivo de jogo", "ゲームファイルではありません", "게임 파일이 아닙니다", "不是游戏文件", "不是遊戲檔案");
        put("Choose the base game's .nsp or .xci file (not the update's).", "Choisissez le fichier .nsp ou .xci du jeu de base (pas celui de la mise à jour).", "Wähle die .nsp- oder .xci-Datei des Grundspiels (nicht die des Updates).", "Scegli il file .nsp o .xci del gioco base (non quello dell'aggiornamento).", "Escolha o arquivo .nsp ou .xci do jogo base (não o da atualização).", "本体ゲームの .nsp または .xci を選んでください（アップデートのものではありません）。", "기본 게임의 .nsp 또는 .xci 파일을 선택하세요(업데이트 파일이 아님).", "请选择本体游戏的 .nsp 或 .xci 文件（不是更新文件）。", "請選擇本體遊戲的 .nsp 或 .xci 檔案（不是更新檔案）。");
        put("Not a keys file", "Ce n'est pas un fichier de clés", "Keine Schlüsseldatei", "Non è un file di chiavi", "Não é um arquivo de chaves", "キーファイルではありません", "키 파일이 아닙니다", "不是密钥文件", "不是金鑰檔案");
        put("That file has no keys (header_key). Choose your console's prod.keys.", "Ce fichier ne contient pas les clés (header_key). Choisissez le prod.keys de votre console.", "Diese Datei enthält keine Schlüssel (header_key). Wähle die prod.keys deiner Konsole.", "Quel file non contiene le chiavi (header_key). Scegli il prod.keys della tua console.", "Esse arquivo não tem as chaves (header_key). Escolha o prod.keys do seu console.", "このファイルにはキー（header_key）がありません。お使いの本体の prod.keys を選んでください。", "이 파일에는 키(header_key)가 없습니다. 본체의 prod.keys 를 선택하세요.", "该文件不含密钥（header_key）。请选择你主机的 prod.keys。", "該檔案不含金鑰（header_key）。請選擇你主機的 prod.keys。");
        put("The game could not be opened", "Impossible d'ouvrir le jeu", "Das Spiel konnte nicht geöffnet werden", "Impossibile aprire il gioco", "Não foi possível abrir o jogo", "ゲームを開けませんでした", "게임을 열 수 없습니다", "无法打开游戏", "無法開啟遊戲");
        put("Your home screen does not support shortcuts.", "Votre écran d'accueil ne prend pas en charge les raccourcis.", "Dein Startbildschirm unterstützt keine Verknüpfungen.", "La tua schermata Home non supporta le scorciatoie.", "Sua tela inicial não aceita atalhos.", "ホーム画面がショートカットに対応していません。", "홈 화면이 바로가기를 지원하지 않습니다.", "你的主屏幕不支持快捷方式。", "你的主畫面不支援捷徑。");
        put("Accept the prompt to put the shortcut with your picture on the home screen. The icon in the app drawer stays: Android does not let an app change its own.", "Acceptez la demande pour placer le raccourci avec votre image sur l'écran d'accueil. L'icône du tiroir d'applications ne change pas : Android ne laisse pas une app changer la sienne.", "Bestätige die Anfrage, um die Verknüpfung mit deinem Bild auf den Startbildschirm zu legen. Das Symbol in der App-Übersicht bleibt: Android lässt Apps ihr eigenes nicht ändern.", "Accetta la richiesta per mettere la scorciatoia con la tua immagine nella schermata Home. L'icona nel cassetto delle app resta: Android non lascia che un'app cambi la propria.", "Aceite o aviso para colocar o atalho com sua imagem na tela inicial. O ícone na gaveta de apps continua: o Android não deixa um app mudar o próprio.", "表示される確認を許可すると、画像付きのショートカットがホーム画面に追加されます。アプリ一覧のアイコンは変わりません（Android ではアプリが自分のアイコンを変更できません）。", "확인 창을 수락하면 이미지가 들어간 바로가기가 홈 화면에 추가됩니다. 앱 서랍의 아이콘은 그대로입니다(Android 는 앱이 자신의 아이콘을 바꾸지 못하게 합니다).", "接受提示即可将带有你图片的快捷方式放到主屏幕。应用抽屉中的图标不变：Android 不允许应用更改自己的图标。", "接受提示即可將帶有你圖片的捷徑放到主畫面。應用程式抽屜中的圖示不變：Android 不允許應用程式更改自己的圖示。");
        put("That picture could not be used.", "Impossible d'utiliser cette image.", "Dieses Bild kann nicht verwendet werden.", "Impossibile usare quell'immagine.", "Não foi possível usar essa imagem.", "その画像は使用できませんでした。", "그 이미지를 사용할 수 없습니다.", "无法使用该图片。", "無法使用該圖片。");
        put("Preparing the game", "Préparation du jeu", "Spiel wird vorbereitet", "Preparazione del gioco", "Preparando o jogo", "ゲームを準備中", "게임 준비 중", "正在准备游戏", "正在準備遊戲");
        put("This is needed only once. Keep the app open until it ends (a few minutes).", "Une seule fois suffit. Laissez l'app ouverte jusqu'à la fin (quelques minutes).", "Das ist nur einmal nötig. Lass die App geöffnet, bis es fertig ist (einige Minuten).", "Serve una sola volta. Tieni l'app aperta finché non termina (qualche minuto).", "Só é preciso uma vez. Deixe o app aberto até terminar (alguns minutos).", "最初の一度だけ必要です。終わるまでアプリを開いたままにしてください（数分）。", "한 번만 필요합니다. 끝날 때까지 앱을 열어 두세요(몇 분).", "只需一次。请保持应用打开直到完成（几分钟）。", "只需一次。請保持應用程式開啟直到完成（幾分鐘）。");
        put("Back", "Retour", "Zurück", "Indietro", "Voltar", "戻る", "뒤로", "返回", "返回");
        put("Opening the game…", "Ouverture du jeu…", "Spiel wird geöffnet…", "Apertura del gioco…", "Abrindo o jogo…", "ゲームを開いています…", "게임을 여는 중…", "正在打开游戏…", "正在開啟遊戲…");
        put("Done. You can play now.", "Terminé. Vous pouvez jouer.", "Fertig. Du kannst jetzt spielen.", "Fatto. Ora puoi giocare.", "Pronto. Já pode jogar.", "完了しました。プレイできます。", "완료되었습니다. 이제 플레이할 수 있습니다.", "完成。现在可以玩了。", "完成。現在可以玩了。");
        put("The game could not be prepared: ", "Le jeu n'a pas pu être préparé : ", "Das Spiel konnte nicht vorbereitet werden: ", "Impossibile preparare il gioco: ", "Não foi possível preparar o jogo: ", "ゲームを準備できませんでした：", "게임을 준비할 수 없습니다: ", "无法准备游戏：", "無法準備遊戲：");
        put("Extracting the game's shaders (%d of %d)…", "Extraction des shaders du jeu (%d sur %d)…", "Shader des Spiels werden extrahiert (%d von %d)…", "Estrazione degli shader del gioco (%d di %d)…", "Extraindo os shaders do jogo (%d de %d)…", "ゲームのシェーダーを抽出中（%d / %d）…", "게임 셰이더 추출 중(%d / %d)…", "正在提取游戏着色器（%d / %d）…", "正在擷取遊戲著色器（%d / %d）…");
        put("Translating the shaders (%d of %d)…", "Traduction des shaders (%d sur %d)…", "Shader werden übersetzt (%d von %d)…", "Traduzione degli shader (%d di %d)…", "Traduzindo os shaders (%d de %d)…", "シェーダーを変換中（%d / %d）…", "셰이더 변환 중(%d / %d)…", "正在转换着色器（%d / %d）…", "正在轉換著色器（%d / %d）…");
        put("Finishing…", "Finalisation…", "Wird abgeschlossen…", "Completamento…", "Finalizando…", "仕上げ中…", "마무리 중…", "正在完成…", "正在完成…");
        // Save import messages
        put("Select a backup ZIP or the folder containing the save files. Your current saves will be backed up before importing.",
                "Choisissez un ZIP de sauvegarde ou le dossier contenant les fichiers de sauvegarde. Vos sauvegardes actuelles seront copiées avant l’importation.",
                "Wähle ein Backup-ZIP oder den Ordner mit den Speicherdaten. Deine aktuellen Spielstände werden vor dem Import gesichert.",
                "Seleziona il file ZIP di backup o la cartella che contiene i salvataggi. I salvataggi attuali saranno copiati prima dell’importazione.",
                "Selecione o ZIP de backup ou a pasta que contém os arquivos salvos. Suas partidas atuais serão copiadas para um backup antes da importação.",
                "バックアップ ZIP またはセーブファイルを含むフォルダーを選択してください。インポート前に現在のセーブデータをバックアップします。",
                "백업 ZIP 또는 저장 파일이 들어 있는 폴더를 선택하세요. 가져오기 전에 현재 세이브를 백업합니다。",
                "选择备份 ZIP 或包含存档文件的文件夹。导入前会先备份当前存档。",
                "選擇備份 ZIP 或包含存檔檔案的資料夾。匯入前會先備份目前的存檔。");
        put("No valid save files were found in that ZIP or folder. Choose a P3P backup or the correct save folder.",
                "Aucun fichier de sauvegarde valide n’a été trouvé dans ce ZIP ou dossier. Choisissez une sauvegarde P3P ou le bon dossier.",
                "In diesem ZIP oder Ordner wurden keine gültigen Speicherdaten gefunden. Wähle ein P3P-Backup oder den richtigen Speicherordner.",
                "Nessun file di salvataggio valido trovato in questo ZIP o cartella. Seleziona un backup P3P o la cartella corretta.",
                "Não encontrei arquivos de save válidos nesse ZIP ou pasta. Escolha um backup exportado pelo P3P ou a pasta correta dos saves.",
                "この ZIP またはフォルダーに有効なセーブデータがありません。P3P のバックアップか正しいセーブフォルダーを選択してください。",
                "이 ZIP 또는 폴더에서 유효한 세이브 파일을 찾지 못했습니다. P3P 백업이나 올바른 세이브 폴더를 선택하세요。",
                "此 ZIP 或文件夹中没有找到有效存档。请选择 P3P 备份或正确的存档文件夹。",
                "此 ZIP 或資料夾中找不到有效存檔。請選擇 P3P 備份或正確的存檔資料夾。");
        put("%d files restored successfully.",
                "%d fichiers restaurés avec succès.", "%d Dateien erfolgreich wiederhergestellt.",
                "%d file ripristinati correttamente.", "%d arquivos restaurados com sucesso.",
                "%d 個のファイルを復元しました。", "%d개 파일을 성공적으로 복원했습니다.",
                "已成功恢复 %d 个文件。", "已成功還原 %d 個檔案。");
        put("\n\nPrevious backup: user/%s.",
                "\n\nSauvegarde précédente : user/%s.", "\n\nVorheriges Backup: user/%s.",
                "\n\nBackup precedente: user/%s.", "\n\nBackup anterior: user/%s.",
                "\n\n以前のバックアップ: user/%s。", "\n\n이전 백업: user/%s.",
                "\n\n之前的备份：user/%s。", "\n\n先前的備份：user/%s。");

        put("Saves restored",
                "Sauvegardes restaurées", "Spielstände wiederhergestellt", "Salvataggi ripristinati",
                "Partidas restauradas", "セーブデータを復元しました", "세이브를 복원했습니다",
                "存档已恢复", "存檔已還原");
        put("No saves found",
                "Aucune sauvegarde trouvée", "Keine Spielstände gefunden", "Nessun salvataggio trovato",
                "Nenhum save encontrado", "セーブデータが見つかりません", "세이브를 찾을 수 없습니다",
                "未找到存档", "找不到存檔");
        // Quit confirmation (GameActivity)
        put("Quit the game?", "Quitter le jeu ?", "Spiel beenden?", "Uscire dal gioco?", "Sair do jogo?",
                "ゲームを終了しますか？", "게임을 종료할까요?", "退出游戏？", "結束遊戲？");
        put("You will go back to the home screen.", "Vous reviendrez à l'écran d'accueil.",
                "Du kehrst zum Startbildschirm zurück.", "Tornerai alla schermata iniziale.",
                "Você voltará para a tela inicial.", "ホーム画面に戻ります。", "홈 화면으로 돌아갑니다.",
                "将返回主界面。", "將返回主畫面。");
        put("Yes", "Oui", "Ja", "Sì", "Sim", "はい", "예", "是", "是");
        put("No", "Non", "Nein", "No", "Não", "いいえ", "아니요", "否", "否");

    }
}
