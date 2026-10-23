using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.Windows.Forms;

namespace Sangita
{
    public class Program
    {
        [STAThread]
        public static void Main(string[] args)
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new LauncherForm());
        }
    }

    public class LauncherForm : Form
    {
        private TextBox txtServerUrl;
        private Button btnLaunch;
        private Label lblStatus;
        private string configPath;

        public LauncherForm()
        {
            string appData = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Sangita");
            if (!Directory.Exists(appData))
            {
                Directory.CreateDirectory(appData);
            }
            configPath = Path.Combine(appData, "server_url.txt");

            string savedUrl = "http://localhost:5000";
            if (File.Exists(configPath))
            {
                string read = File.ReadAllText(configPath).Trim();
                if (!string.IsNullOrEmpty(read))
                {
                    savedUrl = read;
                }
            }

            // Window Setup
            this.Text = "Sangita Desktop";
            this.Size = new Size(460, 320);
            this.StartPosition = FormStartPosition.CenterScreen;
            this.FormBorderStyle = FormBorderStyle.FixedDialog;
            this.MaximizeBox = false;
            this.BackColor = Color.FromArgb(13, 17, 23); // #0D1117
            this.ForeColor = Color.FromArgb(243, 244, 246);

            // Title Label
            Label lblTitle = new Label();
            lblTitle.Text = "🎵 SANGITA";
            lblTitle.Font = new Font("Segoe UI", 18, FontStyle.Bold);
            lblTitle.ForeColor = Color.FromArgb(16, 185, 129); // #10B981 Emerald
            lblTitle.Location = new Point(30, 25);
            lblTitle.AutoSize = true;
            this.Controls.Add(lblTitle);

            // Subtitle
            Label lblSubtitle = new Label();
            lblSubtitle.Text = "Audiophile Cloud Music Player & Sync Client";
            lblSubtitle.Font = new Font("Segoe UI", 9, FontStyle.Regular);
            lblSubtitle.ForeColor = Color.FromArgb(156, 163, 175);
            lblSubtitle.Location = new Point(32, 62);
            lblSubtitle.AutoSize = true;
            this.Controls.Add(lblSubtitle);

            // Server URL Label
            Label lblUrl = new Label();
            lblUrl.Text = "Server Domain / IP Address:";
            lblUrl.Font = new Font("Segoe UI", 9, FontStyle.Bold);
            lblUrl.ForeColor = Color.FromArgb(229, 231, 235);
            lblUrl.Location = new Point(32, 105);
            lblUrl.AutoSize = true;
            this.Controls.Add(lblUrl);

            // Server URL Input
            txtServerUrl = new TextBox();
            txtServerUrl.Text = savedUrl;
            txtServerUrl.Font = new Font("Segoe UI", 11);
            txtServerUrl.Location = new Point(32, 130);
            txtServerUrl.Size = new Size(380, 30);
            txtServerUrl.BackColor = Color.FromArgb(22, 27, 34);
            txtServerUrl.ForeColor = Color.White;
            txtServerUrl.BorderStyle = BorderStyle.FixedSingle;
            this.Controls.Add(txtServerUrl);

            // Status / Hint Label
            lblStatus = new Label();
            lblStatus.Text = "Tip: Enter your Sangita server URL (e.g. http://192.168.1.50:5000)";
            lblStatus.Font = new Font("Segoe UI", 8, FontStyle.Italic);
            lblStatus.ForeColor = Color.FromArgb(107, 114, 128);
            lblStatus.Location = new Point(32, 165);
            lblStatus.Size = new Size(380, 20);
            this.Controls.Add(lblStatus);

            // Launch Button
            btnLaunch = new Button();
            btnLaunch.Text = "Launch Sangita Desktop";
            btnLaunch.Font = new Font("Segoe UI", 11, FontStyle.Bold);
            btnLaunch.Location = new Point(32, 205);
            btnLaunch.Size = new Size(380, 44);
            btnLaunch.BackColor = Color.FromArgb(16, 185, 129); // Emerald
            btnLaunch.ForeColor = Color.White;
            btnLaunch.FlatStyle = FlatStyle.Flat;
            btnLaunch.FlatAppearance.BorderSize = 0;
            btnLaunch.Cursor = Cursors.Hand;
            btnLaunch.Click += (s, e) => LaunchApp();
            this.Controls.Add(btnLaunch);

            // Accept key
            this.AcceptButton = btnLaunch;
        }

        private void LaunchApp()
        {
            string url = txtServerUrl.Text.Trim();
            if (string.IsNullOrEmpty(url))
            {
                MessageBox.Show("Please enter a valid Sangita server URL.", "Sangita", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                return;
            }

            if (!url.StartsWith("http://") && !url.StartsWith("https://"))
            {
                url = "http://" + url;
            }

            try
            {
                File.WriteAllText(configPath, url);
            }
            catch { }

            // Try to find Edge or Chrome for dedicated App Mode
            string edgePath = @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe";
            if (!File.Exists(edgePath))
            {
                edgePath = @"C:\Program Files\Microsoft\Edge\Application\msedge.exe";
            }

            string chromePath = @"C:\Program Files\Google\Chrome\Application\chrome.exe";
            if (!File.Exists(chromePath))
            {
                chromePath = @"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe";
            }

            try
            {
                if (File.Exists(edgePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = edgePath,
                        Arguments = string.Format("--app=\"{0}\" --window-size=1280,840", url),
                        UseShellExecute = true
                    });
                    this.Close();
                    return;
                }
                else if (File.Exists(chromePath))
                {
                    Process.Start(new ProcessStartInfo
                    {
                        FileName = chromePath,
                        Arguments = string.Format("--app=\"{0}\" --window-size=1280,840", url),
                        UseShellExecute = true
                    });
                    this.Close();
                    return;
                }
                else
                {
                    Process.Start(url);
                    this.Close();
                    return;
                }
            }
            catch (Exception ex)
            {
                MessageBox.Show("Failed to launch browser: " + ex.Message, "Sangita Desktop Error", MessageBoxButtons.OK, MessageBoxIcon.Error);
            }
        }
    }
}
