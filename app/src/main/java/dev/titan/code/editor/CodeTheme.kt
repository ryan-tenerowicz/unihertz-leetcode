package dev.titan.code.editor

import android.content.Context
import androidx.core.content.res.ResourcesCompat
import dev.titan.code.R
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

/**
 * Editor look (PLAN §4.2): AMOLED-black `titan-dark` theme, MagicPython highlighting and
 * JetBrains Mono NL (no ligatures). Assets and licences: `app/src/main/assets/`.
 */
object CodeTheme {
    private const val THEME = "titan-dark"
    private const val THEME_PATH = "textmate/titan-dark.json"
    private const val PYTHON_SCOPE = "source.python"
    private var loaded = false

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(context.applicationContext.assets))
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(THEME_PATH)
        val model = ThemeModel(IThemeSource.fromInputStream(stream, THEME_PATH, null), THEME).apply { isDark = true }
        ThemeRegistry.getInstance().loadTheme(model)
        ThemeRegistry.getInstance().setTheme(THEME)
        loaded = true
    }

    fun apply(editor: CodeEditor) {
        ensureLoaded(editor.context)
        val font = ResourcesCompat.getFont(editor.context, R.font.jetbrains_mono_nl_regular)
        editor.typefaceText = font
        editor.typefaceLineNumber = font
        val dp = editor.resources.displayMetrics.density
        editor.setLineNumberMarginLeft(4 * dp)
        editor.setDividerMargin(4 * dp, 8 * dp)
        editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance()).apply {
            // Rev 18 (owner): indent guides and bracket-pair highlight.
            // In-text guides are TitanEditor's IndentGuides (Sora skips them with soft wrap on);
            // Sora's are hidden so wrap-off doesn't draw two sets. The gutter marker stays.
            setColor(EditorColorScheme.BLOCK_LINE, 0x00000000)
            setColor(EditorColorScheme.BLOCK_LINE_CURRENT, 0x00000000)
            setColor(EditorColorScheme.SIDE_BLOCK_LINE, 0xFF6A6A6A.toInt())
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, 0xFFFFCC33.toInt())
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, 0xFF3A3220.toInt())
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, 0x00000000)
            setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BORDER, 0x00000000)
        }
        // Code blocks come from the language's offSide folding (python/language-configuration.json);
        // Sora marks the cursor's block beside the line numbers.
        editor.isBlockLineEnabled = true
        editor.isHighlightCurrentBlock = true
        editor.blockLineWidth = 1f
        editor.isHighlightBracketPair = true
        // No completion until M5; Sora's own auto-indent and pairing stay off (TitanEditor).
        editor.setEditorLanguage(TextMateLanguage.create(PYTHON_SCOPE, false))
    }
}
