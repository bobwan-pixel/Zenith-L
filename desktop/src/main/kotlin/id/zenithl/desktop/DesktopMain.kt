package id.zenithl.desktop

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter

/** Windows desktop workspace. Game integration and account authentication are explicitly unavailable. */
fun main() = SwingUtilities.invokeLater {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
    DesktopWorkspace().show()
}

private class DesktopWorkspace {
    private val base = File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "ZenithL")
    private val workspace = File(base, "scripts").apply { mkdirs() }
    private val runner = locateRunner()
    private val files = DefaultListModel<File>()
    private val list = JList(files)
    private val editor = JTextArea()
    private val console = JTextArea()
    private val frame = JFrame("Zenith'L • Windows Workspace")
    private var selected: File? = null
    private var process: Process? = null

    private fun locateRunner(): File {
        System.getenv("ZENITH_LUA_RUNNER")?.let { return File(it) }
        val jarDir = runCatching { File(DesktopWorkspace::class.java.protectionDomain.codeSource.location.toURI()).parentFile }.getOrNull()
        return listOfNotNull(
            jarDir?.let { File(it, "zenith-lua.exe") },
            File("desktop/native/build/Release/zenith-lua.exe"),
        ).firstOrNull(File::isFile) ?: File("desktop/native/build/Release/zenith-lua.exe")
    }

    fun show() {
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.minimumSize = Dimension(900, 620)
        frame.setSize(1180, 760)
        frame.contentPane.background = Color(11, 17, 31)
        frame.layout = BorderLayout(12, 12)
        frame.add(sidebar(), BorderLayout.WEST)
        frame.add(workspacePanel(), BorderLayout.CENTER)
        frame.add(header(), BorderLayout.NORTH)
        frame.add(JLabel(" Windows workspace • Game Bridge UNAVAILABLE • Authentication NOT CONFIGURED ").apply { foreground = Color(160, 177, 199) }, BorderLayout.SOUTH)
        refreshFiles()
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    private fun header() = JPanel(BorderLayout()).apply {
        background = Color(15, 26, 43); border = BorderFactory.createEmptyBorder(14, 18, 14, 18)
        add(JLabel("ZENITH'L").apply { foreground = Color(237, 243, 255); font = Font("Segoe UI", Font.BOLD, 22) }, BorderLayout.WEST)
        add(JLabel("WINDOWS • SCRIPT WORKSPACE").apply { foreground = Color(141, 162, 190) }, BorderLayout.EAST)
    }

    private fun sidebar() = JPanel(GridLayout(0, 1, 0, 8)).apply {
        background = Color(15, 26, 43); border = BorderFactory.createEmptyBorder(18, 12, 18, 12)
        preferredSize = Dimension(180, 0)
        listOf("Home", "Accounts", "Scripts", "Safety", "Settings").forEach { title ->
            add(JButton(title).apply {
                isFocusPainted = false
                addActionListener { showSection(title) }
            })
        }
    }

    private fun workspacePanel() = JPanel(BorderLayout(10, 10)).apply {
        background = Color(11, 17, 31); border = BorderFactory.createEmptyBorder(4, 0, 8, 12)
        list.fixedCellHeight = 34
        list.addListSelectionListener { if (!it.valueIsAdjusting) open(list.selectedValue) }
        add(JScrollPane(list).apply { preferredSize = Dimension(200, 0) }, BorderLayout.WEST)
        editor.background = Color(15, 26, 43); editor.foreground = Color(230, 238, 250); editor.caretColor = Color(95, 184, 255)
        editor.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        add(JScrollPane(editor), BorderLayout.CENTER)
        console.isEditable = false; console.background = Color(7, 13, 23); console.foreground = Color(180, 200, 220)
        console.rows = 7
        val controls = JPanel(BorderLayout()).apply {
            background = Color(11, 17, 31)
            val buttons = JPanel().apply {
                background = Color(11, 17, 31)
                add(JButton("New").apply { addActionListener { createScript() } })
                add(JButton("Import .lua").apply { addActionListener { importScript() } })
                add(JButton("Save").apply { addActionListener { save() } })
                add(JButton("Run").apply { addActionListener { runScript() } })
                add(JButton("Stop").apply { addActionListener { stopScript() } })
            }
            add(buttons, BorderLayout.NORTH); add(JScrollPane(console), BorderLayout.CENTER)
        }
        add(controls, BorderLayout.SOUTH)
    }

    private fun showSection(title: String) {
        if (title == "Scripts") return
        val description = when (title) {
            "Home" -> "Windows workspace ready. Native Lua runtime: ${if (runner.isFile) "available" else "not built"}."
            "Accounts" -> "Account authentication is NOT CONFIGURED. This desktop target stores no passwords or tokens."
            "Safety" -> "Script process controls and bounded Lua runner are available when built. Game/session monitoring is unavailable."
            else -> "Settings are stored per-user under %APPDATA%\\ZenithL. Game Bridge remains UNAVAILABLE."
        }
        JOptionPane.showMessageDialog(frame, description, "Zenith'L • $title", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun refreshFiles() {
        files.clear(); workspace.listFiles { file -> file.isFile && file.extension.equals("lua", true) }
            ?.sortedBy { it.name.lowercase() }?.forEach(files::addElement)
    }
    private fun open(file: File?) {
        selected = file
        editor.text = file?.takeIf(File::isFile)?.readText() ?: "-- Lua 5.4 script\nprint('Hello from Zenith\\'L')\n"
    }
    private fun createScript() {
        val name = JOptionPane.showInputDialog(frame, "Script name", "New script", JOptionPane.PLAIN_MESSAGE)?.trim().orEmpty()
        if (name.isBlank()) return
        val clean = name.removeSuffix(".lua").takeIf { it.matches(Regex("[A-Za-z0-9 _.-]{1,64}")) }
        if (clean == null) { JOptionPane.showMessageDialog(frame, "Invalid script name"); return }
        open(File(workspace, "$clean.lua")); save()
    }
    private fun importScript() {
        val chooser = javax.swing.JFileChooser().apply { fileFilter = FileNameExtensionFilter("Lua scripts (*.lua)", "lua") }
        if (chooser.showOpenDialog(frame) != javax.swing.JFileChooser.APPROVE_OPTION) return
        val source = chooser.selectedFile
        if (!source.extension.equals("lua", true) || source.length() > 1_048_576) { JOptionPane.showMessageDialog(frame, "Choose a .lua file up to 1 MiB"); return }
        open(File(workspace, source.name)); editor.text = source.readText(); save()
    }
    private fun save() {
        val file = selected ?: run {
            val name = "script-${System.currentTimeMillis()}.lua"; File(workspace, name).also { selected = it }
        }
        runCatching { require(file.canonicalFile.parentFile == workspace.canonicalFile); file.writeText(editor.text); refreshFiles(); list.setSelectedValue(file, true) }
            .onFailure { JOptionPane.showMessageDialog(frame, "Could not save script: ${it.message}") }
    }
    private fun runScript() {
        val file = selected ?: run { save(); selected } ?: return
        if (!runner.isFile) { append("Native Lua 5.4 runner missing. Build it with desktop/native/CMakeLists.txt on Windows."); return }
        stopScript()
        process = runCatching { ProcessBuilder(runner.absolutePath, file.absolutePath).redirectErrorStream(true).start() }
            .onFailure { append("Could not start Lua runner: ${it.message}") }.getOrNull()
        val current = process ?: return
        Thread({ current.inputStream.bufferedReader().useLines { lines -> lines.forEach { line -> SwingUtilities.invokeLater { append(line) } } } }, "zenith-desktop-lua-output").apply { isDaemon = true; start() }
        Thread({ val code = current.waitFor(); SwingUtilities.invokeLater { append("Lua exited with code $code") } }, "zenith-desktop-lua-watch").apply { isDaemon = true; start() }
    }
    private fun stopScript() { process?.let { if (it.isAlive) it.destroyForcibly() }; process = null }
    private fun append(line: String) { console.append("$line\n"); console.caretPosition = console.document.length }
}
