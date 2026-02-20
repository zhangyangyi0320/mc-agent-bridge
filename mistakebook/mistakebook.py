import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import csv
import os
import json
from datetime import datetime
import threading
import tkinter.font as tkFont

class MistakeBookApp:
    def __init__(self, root):
        self.root = root
        self.root.title("错题本管理系统")
        self.root.geometry("1000x700")
        
        # 初始化字体
        self.default_font = tkFont.nametofont("TkDefaultFont")
        self.default_font_size = self.default_font.actual()['size']
        
        # 数据文件路径
        self.data_file = "mistakebook_data.csv"
        self.config_file = "config.json"
        
        # 初始化配置
        self.load_config()
        
        # 初始化数据
        self.data = []
        self.load_data()
        
        # 创建界面
        self.create_widgets()
        
        # 设置主题
        self.apply_theme()
        
        # 绑定窗口大小调整事件
        self.root.bind('<Configure>', self.on_window_resize)
        
    def load_config(self):
        """加载配置文件，如果没有则创建默认配置"""
        default_config = {
            "theme": "light",  # "light" or "dark"
            "font_size": 12,
            "window_width": 1000,
            "window_height": 700
        }
        
        if os.path.exists(self.config_file):
            try:
                with open(self.config_file, 'r', encoding='utf-8') as f:
                    self.config = json.load(f)
            except:
                self.config = default_config
        else:
            self.config = default_config
    
    def save_config(self):
        """保存配置到文件"""
        with open(self.config_file, 'w', encoding='utf-8') as f:
            json.dump(self.config, f, ensure_ascii=False, indent=2)
    
    def load_data(self):
        """从CSV文件加载数据"""
        if os.path.exists(self.data_file):
            with open(self.data_file, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                self.data = [row for row in reader]
        else:
            # 创建CSV文件并写入表头
            with open(self.data_file, 'w', newline='', encoding='utf-8') as f:
                fieldnames = ['时间', '科目', '题干', '正确答案', '附件路径', '难度']
                writer = csv.DictWriter(f, fieldnames=fieldnames)
                writer.writeheader()
                self.data = []
    
    def save_data(self):
        """保存数据到CSV文件"""
        with open(self.data_file, 'w', newline='', encoding='utf-8') as f:
            fieldnames = ['时间', '科目', '题干', '正确答案', '附件路径', '难度']
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            writer.writerows(self.data)

    def create_widgets(self):
        """创建界面组件"""
        # 创建主框架
        main_frame = ttk.Frame(self.root)
        main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # 创建菜单栏
        self.create_menu(main_frame)
        
        # 创建主界面
        self.create_main_interface(main_frame)
        
        # 创建添加错题界面
        self.create_add_interface(main_frame)
        
        # 初始显示主界面
        self.show_main_interface()

    def create_menu(self, parent):
        """创建菜单栏"""
        menubar = tk.Menu(parent.winfo_toplevel())
        
        # 文件菜单
        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="从TXT导入", command=self.import_from_txt)
        file_menu.add_separator()
        file_menu.add_command(label="导出为PDF", command=self.export_to_pdf)
        file_menu.add_command(label="导出为TXT", command=self.export_to_txt)
        file_menu.add_separator()
        file_menu.add_command(label="退出", command=self.root.quit)
        menubar.add_cascade(label="文件", menu=file_menu)
        
        # 设置菜单
        settings_menu = tk.Menu(menubar, tearoff=0)
        settings_menu.add_command(label="主题切换", command=self.toggle_theme)
        settings_menu.add_command(label="字体设置", command=self.open_font_settings)
        menubar.add_cascade(label="设置", menu=settings_menu)
        
        self.root.config(menu=menubar)

    def create_main_interface(self, parent):
        """创建主界面"""
        self.main_frame = ttk.Frame(parent)
        
        # 搜索框
        search_frame = ttk.LabelFrame(self.main_frame, text="🔍 搜索", padding=10)
        search_frame.pack(fill=tk.X, pady=5)
        
        search_container = ttk.Frame(search_frame)
        search_container.pack(fill=tk.X)
        
        self.search_var = tk.StringVar()
        search_entry = ttk.Entry(search_container, textvariable=self.search_var, width=30, font=('Microsoft YaHei', 10))
        search_entry.pack(side=tk.LEFT, padx=(0, 10), fill=tk.X, expand=True)
        search_entry.bind('<KeyRelease>', self.search_data)
        
        ttk.Button(search_container, text="🔍 搜索", command=self.search_data).pack(side=tk.LEFT, padx=(0, 5))
        ttk.Button(search_container, text="🔄 刷新", command=self.refresh_data).pack(side=tk.LEFT)
        
        # 表格框架
        table_frame = ttk.LabelFrame(self.main_frame, text="📚 错题列表", padding=10)
        table_frame.pack(fill=tk.BOTH, expand=True, pady=5)
        
        # 创建表格
        columns = ('时间', '科目', '题干', '正确答案', '附件', '难度')
        self.tree = ttk.Treeview(table_frame, columns=columns, show='headings', height=12)
        
        # 设置列标题和宽度
        col_widths = {'时间': 140, '科目': 100, '题干': 220, '正确答案': 180, '附件': 80, '难度': 80}
        for col in columns:
            self.tree.heading(col, text=col, anchor=tk.CENTER)
            self.tree.column(col, width=col_widths.get(col, 100), anchor=tk.W)
        
        # 添加滚动条
        v_scrollbar = ttk.Scrollbar(table_frame, orient=tk.VERTICAL, command=self.tree.yview)
        h_scrollbar = ttk.Scrollbar(table_frame, orient=tk.HORIZONTAL, command=self.tree.xview)
        self.tree.configure(yscrollcommand=v_scrollbar.set, xscrollcommand=h_scrollbar.set)
        
        # 布局
        self.tree.grid(row=0, column=0, sticky='nsew')
        v_scrollbar.grid(row=0, column=1, sticky='ns')
        h_scrollbar.grid(row=1, column=0, sticky='ew')
        
        table_frame.grid_rowconfigure(0, weight=1)
        table_frame.grid_columnconfigure(0, weight=1)
        
        # 按钮框架 - 使用更紧凑的布局
        button_frame = ttk.Frame(self.main_frame)
        button_frame.pack(fill=tk.X, pady=5)
        
        # 创建样式
        style = ttk.Style()
        style.configure('Action.TButton', font=('Microsoft YaHei', 10))
        
        # 使用网格布局使按钮排列更整齐
        ttk.Button(button_frame, text="➕ 添加", command=self.show_add_interface, style='Action.TButton').grid(row=0, column=0, padx=2, sticky='ew')
        ttk.Button(button_frame, text="❌ 删除", command=self.delete_selected, style='Action.TButton').grid(row=0, column=1, padx=2, sticky='ew')
        ttk.Button(button_frame, text="✏️ 编辑", command=self.edit_selected, style='Action.TButton').grid(row=0, column=2, padx=2, sticky='ew')
        ttk.Button(button_frame, text="📖 详情", command=self.view_detail, style='Action.TButton').grid(row=0, column=3, padx=2, sticky='ew')
        
        # 配置列权重，使按钮平均分布
        for i in range(4):
            button_frame.grid_columnconfigure(i, weight=1)
        
        # 加载数据到表格
        self.refresh_data()

    def create_add_interface(self, parent):
        """创建添加错题界面"""
        self.add_frame = ttk.Frame(parent)
        
        # 表单框架
        form_frame = ttk.LabelFrame(self.add_frame, text="➕ 添加错题", padding=20)
        form_frame.pack(fill=tk.BOTH, expand=True, pady=10)
        
        # 时间
        time_frame = ttk.Frame(form_frame)
        time_frame.grid(row=0, column=0, columnspan=2, sticky=tk.W, pady=5)
        ttk.Label(time_frame, text="📅 时间:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        self.time_var = tk.StringVar(value=datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
        ttk.Entry(time_frame, textvariable=self.time_var, state='readonly', width=25).pack(side=tk.LEFT, padx=(10, 0))
        
        # 科目
        subject_frame = ttk.Frame(form_frame)
        subject_frame.grid(row=1, column=0, columnspan=2, sticky=tk.W, pady=5)
        ttk.Label(subject_frame, text="📘 科目:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        self.subject_var = tk.StringVar()
        subject_combo = ttk.Combobox(subject_frame, textvariable=self.subject_var, 
                                    values=["语文", "数学", "英语", "物理", "化学", "生物", "历史", "地理", "道法"], 
                                    state="readonly", width=23)
        subject_combo.pack(side=tk.LEFT, padx=(10, 0))
        subject_combo.set("语文")
        
        # 题干
        ttk.Label(form_frame, text="📝 题干:", font=('Microsoft YaHei', 10, 'bold')).grid(row=2, column=0, sticky=tk.NW, pady=5)
        self.question_frame = ttk.Frame(form_frame)
        self.question_frame.grid(row=2, column=1, pady=5, padx=(10, 0), sticky='nsew')
        
        self.question_text = tk.Text(self.question_frame, height=6, wrap=tk.WORD, font=('Microsoft YaHei', 10))
        question_scrollbar = ttk.Scrollbar(self.question_frame, orient=tk.VERTICAL, command=self.question_text.yview)
        self.question_text.configure(yscrollcommand=question_scrollbar.set)
        
        self.question_text.grid(row=0, column=0, sticky='nsew')
        question_scrollbar.grid(row=0, column=1, sticky='ns')
        self.question_frame.grid_rowconfigure(0, weight=1)
        self.question_frame.grid_columnconfigure(0, weight=1)
        
        # 正确答案
        ttk.Label(form_frame, text="✅ 正确答案:", font=('Microsoft YaHei', 10, 'bold')).grid(row=3, column=0, sticky=tk.NW, pady=5)
        self.answer_frame = ttk.Frame(form_frame)
        self.answer_frame.grid(row=3, column=1, pady=5, padx=(10, 0), sticky='nsew')
        
        self.answer_text = tk.Text(self.answer_frame, height=6, wrap=tk.WORD, font=('Microsoft YaHei', 10))
        answer_scrollbar = ttk.Scrollbar(self.answer_frame, orient=tk.VERTICAL, command=self.answer_text.yview)
        self.answer_text.configure(yscrollcommand=answer_scrollbar.set)
        
        self.answer_text.grid(row=0, column=0, sticky='nsew')
        answer_scrollbar.grid(row=0, column=1, sticky='ns')
        self.answer_frame.grid_rowconfigure(0, weight=1)
        self.answer_frame.grid_columnconfigure(0, weight=1)
        
        # 附件
        attachment_frame = ttk.Frame(form_frame)
        attachment_frame.grid(row=4, column=0, columnspan=2, sticky=tk.W, pady=5)
        ttk.Label(attachment_frame, text="📎 附件:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        self.attachment_frame = ttk.Frame(attachment_frame)
        self.attachment_frame.pack(side=tk.LEFT, padx=(10, 0))
        
        self.attachment_var = tk.StringVar()
        ttk.Entry(self.attachment_frame, textvariable=self.attachment_var, width=45, state='readonly').grid(row=0, column=0, padx=(0, 5))
        ttk.Button(self.attachment_frame, text="📁 选择文件", command=self.select_attachment).grid(row=0, column=1)
        
        # 难度
        difficulty_frame = ttk.Frame(form_frame)
        difficulty_frame.grid(row=5, column=0, columnspan=2, sticky=tk.W, pady=5)
        ttk.Label(difficulty_frame, text="📊 难度:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        self.difficulty_var = tk.StringVar(value="中等")
        difficulty_combo = ttk.Combobox(difficulty_frame, textvariable=self.difficulty_var, 
                                       values=["简单", "中等", "困难"], state="readonly", width=23)
        difficulty_combo.pack(side=tk.LEFT, padx=(10, 0))
        
        # 按钮框架
        button_frame = ttk.Frame(form_frame)
        button_frame.grid(row=6, column=0, columnspan=2, pady=20)
        
        style = ttk.Style()
        style.configure('Save.TButton', font=('Microsoft YaHei', 10, 'bold'))
        
        ttk.Button(button_frame, text="💾 保存", command=self.save_mistake, style='Save.TButton').pack(side=tk.LEFT, padx=(0, 20))
        ttk.Button(button_frame, text="↩️ 取消", command=self.show_main_interface).pack(side=tk.LEFT)
        
        # 配置行权重使文本框可以扩展
        form_frame.grid_rowconfigure(2, weight=1)
        form_frame.grid_rowconfigure(3, weight=1)
        form_frame.grid_columnconfigure(1, weight=1)

    def select_attachment(self):
        """选择附件文件"""
        file_path = filedialog.askopenfilename(
            title="选择附件文件",
            filetypes=[
                ("图片文件", "*.jpg *.jpeg *.png *.gif *.bmp"),
                ("文档文件", "*.pdf *.doc *.docx *.txt"),
                ("所有文件", "*.*")
            ]
        )
        if file_path:
            self.attachment_var.set(file_path)

    def save_mistake(self):
        """保存错题"""
        # 获取数据
        time_val = self.time_var.get()
        subject_val = self.subject_var.get()
        question_val = self.question_text.get("1.0", tk.END).strip()
        answer_val = self.answer_text.get("1.0", tk.END).strip()
        attachment_val = self.attachment_var.get()
        difficulty_val = self.difficulty_var.get()
        
        # 验证输入
        if not question_val or not answer_val:
            messagebox.showwarning("警告", "题干和正确答案不能为空！")
            return
        
        # 创建数据字典
        new_data = {
            '时间': time_val,
            '科目': subject_val,
            '题干': question_val,
            '正确答案': answer_val,
            '附件路径': attachment_val,
            '难度': difficulty_val
        }
        
        # 添加到数据列表
        self.data.append(new_data)
        
        # 保存到文件
        self.save_data()
        
        # 刷新界面
        self.refresh_data()
        
        # 返回主界面
        self.show_main_interface()
        
        messagebox.showinfo("成功", "错题已保存！")

    def refresh_data(self):
        """刷新数据显示"""
        # 清空现有数据
        for item in self.tree.get_children():
            self.tree.delete(item)
        
        # 添加数据到表格
        for row in self.data:
            # 截断题干和答案以适应表格显示
            question = row['题干'][:50] + '...' if len(row['题干']) > 50 else row['题干']
            answer = row['正确答案'][:30] + '...' if len(row['正确答案']) > 30 else row['正确答案']
            attachment = "有" if row['附件路径'] else "无"
            
            self.tree.insert('', tk.END, values=(
                row['时间'],
                row['科目'],
                question,
                answer,
                attachment,
                row['难度']
            ))

    def search_data(self, event=None):
        """搜索数据"""
        query = self.search_var.get().lower()
        if not query:
            self.refresh_data()
            return
        
        # 清空现有数据
        for item in self.tree.get_children():
            self.tree.delete(item)
        
        # 搜索匹配的数据
        for row in self.data:
            if (query in row['时间'].lower() or 
                query in row['科目'].lower() or 
                query in row['题干'].lower() or 
                query in row['正确答案'].lower() or 
                query in row['难度'].lower()):
                
                # 截断题干和答案以适应表格显示
                question = row['题干'][:50] + '...' if len(row['题干']) > 50 else row['题干']
                answer = row['正确答案'][:30] + '...' if len(row['正确答案']) > 30 else row['正确答案']
                attachment = "有" if row['附件路径'] else "无"
                
                self.tree.insert('', tk.END, values=(
                    row['时间'],
                    row['科目'],
                    question,
                    answer,
                    attachment,
                    row['难度']
                ))

    def delete_selected(self):
        """删除选中的错题"""
        selected_items = self.tree.selection()
        if not selected_items:
            messagebox.showwarning("警告", "请先选择要删除的错题！")
            return
        
        if messagebox.askyesno("确认", "确定要删除选中的错题吗？"):
            for item in selected_items:
                # 获取选中行的值
                values = self.tree.item(item, 'values')
                # 在数据列表中查找并删除对应的数据
                for i, row in enumerate(self.data):
                    if row['时间'] == values[0] and row['科目'] == values[1] and \
                       row['题干'][:50] + '...' if len(row['题干']) > 50 else row['题干'] == values[2]:
                        del self.data[i]
                        break
            
            # 保存并刷新
            self.save_data()
            self.refresh_data()

    def edit_selected(self):
        """编辑选中的错题"""
        selected_items = self.tree.selection()
        if not selected_items:
            messagebox.showwarning("警告", "请先选择要编辑的错题！")
            return
        
        # 获取选中行的值
        item = selected_items[0]
        values = self.tree.item(item, 'values')
        
        # 在数据列表中查找完整数据
        for i, row in enumerate(self.data):
            if row['时间'] == values[0] and row['科目'] == values[1]:
                # 将数据填充到添加界面的控件中
                self.time_var.set(row['时间'])
                self.subject_var.set(row['科目'])
                self.question_text.delete("1.0", tk.END)
                self.question_text.insert("1.0", row['题干'])
                self.answer_text.delete("1.0", tk.END)
                self.answer_text.insert("1.0", row['正确答案'])
                self.attachment_var.set(row['附件路径'])
                self.difficulty_var.set(row['难度'])
                
                # 记录当前编辑的索引
                self.current_edit_index = i
                
                # 显示添加界面（实际上是编辑界面）
                self.show_add_interface()
                break

    def view_detail(self):
        """查看错题详情"""
        selected_items = self.tree.selection()
        if not selected_items:
            messagebox.showwarning("警告", "请先选择要查看的错题！")
            return
        
        # 获取选中行的值
        item = selected_items[0]
        values = self.tree.item(item, 'values')
        
        # 在数据列表中查找完整数据
        for row in self.data:
            if row['时间'] == values[0] and row['科目'] == values[1]:
                # 创建详情窗口
                detail_window = tk.Toplevel(self.root)
                detail_window.title("📖 错题详情")
                detail_window.geometry("700x600")
                detail_window.transient(self.root)
                detail_window.grab_set()  # 模态窗口
                
                # 创建主框架
                main_frame = ttk.Frame(detail_window)
                main_frame.pack(fill=tk.BOTH, expand=True, padx=15, pady=15)
                
                # 标题
                title_label = tk.Label(main_frame, text="🔍 错题详情", font=('Microsoft YaHei', 16, 'bold'))
                title_label.pack(anchor=tk.W, pady=(0, 15))
                
                # 创建文本框和滚动条
                text_frame = ttk.Frame(main_frame)
                text_frame.pack(fill=tk.BOTH, expand=True)
                
                detail_text = tk.Text(text_frame, wrap=tk.WORD, font=('Microsoft YaHei', 11), 
                                     padx=10, pady=10, spacing1=5, spacing3=5)
                scrollbar = ttk.Scrollbar(text_frame, orient=tk.VERTICAL, command=detail_text.yview)
                detail_text.configure(yscrollcommand=scrollbar.set)
                
                # 插入详细内容
                detail_content = f"""📅 时间: {row['时间']}

📘 科目: {row['科目']}

📊 难度: {row['难度']}

📝 题干:
{row['题干']}

✅ 正确答案:
{row['正确答案']}

📎 附件路径: {row['附件路径'] if row['附件路径'] else '无'}
"""
                detail_text.insert(tk.END, detail_content)
                detail_text.config(state=tk.DISABLED)  # 设置为只读
                
                # 布局
                detail_text.grid(row=0, column=0, sticky='nsew')
                scrollbar.grid(row=0, column=1, sticky='ns')
                
                text_frame.grid_rowconfigure(0, weight=1)
                text_frame.grid_columnconfigure(0, weight=1)
                main_frame.grid_rowconfigure(1, weight=1)
                main_frame.grid_columnconfigure(0, weight=1)
                
                break

    def show_main_interface(self):
        """显示主界面"""
        self.add_frame.pack_forget()
        self.main_frame.pack(fill=tk.BOTH, expand=True)

    def show_add_interface(self):
        """显示添加界面"""
        self.main_frame.pack_forget()
        self.add_frame.pack(fill=tk.BOTH, expand=True)
        
        # 如果是在编辑模式，标题改为"编辑错题"
        if hasattr(self, 'current_edit_index'):
            for child in self.add_frame.winfo_children():
                if isinstance(child, ttk.LabelFrame):
                    child.config(text="编辑错题")
        else:
            for child in self.add_frame.winfo_children():
                if isinstance(child, ttk.LabelFrame):
                    child.config(text="添加错题")

    def toggle_theme(self):
        """切换主题"""
        if self.config['theme'] == 'light':
            self.config['theme'] = 'dark'
        else:
            self.config['theme'] = 'light'
        
        self.apply_theme()
        self.save_config()

    def apply_theme(self):
        """应用主题"""
        theme = self.config['theme']
        
        if theme == 'dark':
            # 暗色主题
            self.root.configure(bg='#2e2e2e')
            style = ttk.Style()
            style.theme_use('clam')
            
            # 配置颜色
            style.configure('TFrame', background='#2e2e2e')
            style.configure('TLabel', background='#2e2e2e', foreground='white')
            style.configure('TButton', background='#4a4a4a', foreground='white')
            style.configure('TEntry', fieldbackground='#555555', foreground='white')
            style.configure('Treeview', background='#3e3e3e', foreground='white', fieldbackground='#3e3e3e')
            style.map('TButton', background=[('active', '#6a6a6a')])
        else:
            # 亮色主题
            self.root.configure(bg='white')
            style = ttk.Style()
            style.theme_use('default')
            
            # 配置颜色
            style.configure('TFrame', background='white')
            style.configure('TLabel', background='white', foreground='black')
            style.configure('TButton', background='#f0f0f0', foreground='black')
            style.configure('TEntry', fieldbackground='white', foreground='black')
            style.configure('Treeview', background='white', foreground='black', fieldbackground='white')
            style.map('TButton', background=[('active', '#e0e0e0')])

    def on_window_resize(self, event):
        """窗口大小调整事件"""
        # 仅在根窗口调整大小时才触发（避免子控件调整大小时触发）
        if event.widget == self.root:
            self.config['window_width'] = event.width
            self.config['window_height'] = event.height
            self.save_config()
            
            # 根据窗口大小调整字体
            self.adjust_font_size()

    def adjust_font_size(self):
        """根据窗口大小调整字体"""
        # 获取当前窗口大小
        width = self.root.winfo_width() or self.config['window_width']
        height = self.root.winfo_height() or self.config['window_height']
        
        # 根据窗口大小动态调整字体大小
        new_font_size = max(10, min(16, int((width + height) / 120)))
        
        if new_font_size != self.config['font_size']:
            self.config['font_size'] = new_font_size
            self.save_config()
            
            # 更新默认字体大小
            self.default_font.configure(size=new_font_size)
            
            # 更新特定控件的字体
            self.update_all_fonts(self.default_font.actual()['family'], new_font_size)

    def update_font_size(self, widget, font_size):
        """递归更新控件及其子控件的字体大小"""
        try:
            # 尝试更新控件的字体
            if hasattr(widget, 'configure'):
                # 获取当前字体配置
                current_font = widget.cget('font')
                
                if isinstance(current_font, str):
                    # 如果是字体名称，创建新字体
                    new_font = tkFont.Font(font=widget.cget('font'))
                    new_font.configure(size=font_size)
                    widget.configure(font=new_font)
                elif isinstance(current_font, tuple):
                    # 如果是字体元组 (family, size, ...)
                    font_family = current_font[0] if current_font else "TkDefaultFont"
                    new_font = tkFont.Font(family=font_family, size=font_size)
                    widget.configure(font=new_font)
                elif hasattr(current_font, 'configure'):
                    # 如果是Font对象，直接配置
                    current_font.configure(size=font_size)
        except tk.TclError:
            # 某些控件可能不支持font属性，忽略错误
            pass
        
        # 递归更新子控件
        for child in widget.winfo_children():
            self.update_font_size(child, font_size)

    def open_font_settings(self):
        """打开字体设置窗口"""
        font_window = tk.Toplevel(self.root)
        font_window.title("🎨 字体设置")
        font_window.geometry("400x200")
        font_window.transient(self.root)  # 设置为临时窗口
        font_window.grab_set()  # 模态窗口
        
        # 主框架
        main_frame = ttk.Frame(font_window, padding=20)
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # 标题
        title_label = tk.Label(main_frame, text="🎨 字体设置", font=('Microsoft YaHei', 14, 'bold'))
        title_label.grid(row=0, column=0, columnspan=2, pady=(0, 20))
        
        # 字体族选择
        font_frame = ttk.Frame(main_frame)
        font_frame.grid(row=1, column=0, columnspan=2, sticky=tk.W, pady=5)
        ttk.Label(font_frame, text="🔤 字体:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        
        font_families = tkFont.families()
        # 选取常用字体，包括中英文
        common_fonts = []
        for f in font_families:
            lower_f = f.lower()
            if any(x in lower_f for x in ['microsoft', 'sim', 'song', 'hei', 'kai', 'deja', 'liberation', 'ubuntu', 'times', 'arial', 'helvetica', 'consolas', 'courier', 'comic', 'calibri', 'cambria', 'georgia', 'tahoma', 'trebuchet', 'verdana']):
                common_fonts.append(f)
        # 添加一些常见的中文字体
        chinese_fonts = ['SimSun', 'SimHei', 'Microsoft YaHei', 'KaiTi', 'FangSong', 'NSimSun', 'Microsoft JhengHei']
        for font in chinese_fonts:
            if font in font_families and font not in common_fonts:
                common_fonts.append(font)
        common_fonts = common_fonts[:30]  # 限制显示数量
        self.font_family_var = tk.StringVar(value=self.default_font.actual()['family'])
        font_combo = ttk.Combobox(font_frame, textvariable=self.font_family_var, values=common_fonts, state="readonly", width=25)
        font_combo.pack(side=tk.LEFT, padx=(10, 0))
        
        # 字体大小选择
        size_frame = ttk.Frame(main_frame)
        size_frame.grid(row=2, column=0, columnspan=2, sticky=tk.W, pady=15)
        ttk.Label(size_frame, text="📏 字体大小:", font=('Microsoft YaHei', 10, 'bold')).pack(side=tk.LEFT)
        
        self.font_size_var = tk.IntVar(value=self.config.get('font_size', self.default_font_size))
        size_spinbox = tk.Spinbox(size_frame, from_=8, to=24, textvariable=self.font_size_var, width=10, font=('Microsoft YaHei', 10))
        size_spinbox.pack(side=tk.LEFT, padx=(10, 0))
        
        # 按钮框架
        button_frame = ttk.Frame(main_frame)
        button_frame.grid(row=3, column=0, columnspan=2, pady=30)
        
        style = ttk.Style()
        style.configure('Apply.TButton', font=('Microsoft YaHei', 10, 'bold'))
        
        ttk.Button(button_frame, text="✅ 应用", command=lambda: self.apply_font_settings(font_window), style='Apply.TButton').pack(side=tk.LEFT, padx=(0, 20))
        ttk.Button(button_frame, text="❌ 取消", command=font_window.destroy).pack(side=tk.LEFT)

    def apply_font_settings(self, window):
        """应用字体设置"""
        new_family = self.font_family_var.get()
        new_size = self.font_size_var.get()
        
        # 更新默认字体
        self.default_font.configure(family=new_family, size=new_size)
        
        # 更新配置
        self.config['font_family'] = new_family
        self.config['font_size'] = new_size
        self.save_config()
        
        # 更新所有控件的字体
        self.update_all_fonts(new_family, new_size)
        
        # 关闭窗口
        window.destroy()

    def update_all_fonts(self, font_family, font_size):
        """更新所有控件的字体"""
        self.update_font_size(self.root, font_size)

    def export_to_pdf(self):
        """导出数据到PDF"""
        try:
            from reportlab.lib.pagesizes import A4
            from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer
            from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
            from reportlab.lib.units import inch
            from reportlab.lib import colors
            
            # 询问保存位置
            file_path = filedialog.asksaveasfilename(
                defaultextension=".pdf",
                filetypes=[("PDF files", "*.pdf"), ("All files", "*.*")],
                title="导出为PDF"
            )
            
            if not file_path:
                return
            
            # 创建PDF文档
            doc = SimpleDocTemplate(file_path, pagesize=A4)
            story = []
            
            # 标题样式
            title_style = ParagraphStyle(
                'CustomTitle',
                parent=getSampleStyleSheet()['Title'],
                fontSize=20,
                spaceAfter=30,
                alignment=1  # 居中
            )
            
            # 正文样式
            content_style = ParagraphStyle(
                'CustomContent',
                parent=getSampleStyleSheet()['Normal'],
                fontSize=12,
                spaceAfter=12
            )
            
            # 添加标题
            title = Paragraph("错题本导出", title_style)
            story.append(title)
            story.append(Spacer(1, 0.2 * inch))
            
            # 添加数据
            for i, row in enumerate(self.data, 1):
                story.append(Paragraph(f"第{i}题", content_style))
                story.append(Paragraph(f"时间: {row['时间']}", content_style))
                story.append(Paragraph(f"科目: {row['科目']}", content_style))
                story.append(Paragraph(f"难度: {row['难度']}", content_style))
                story.append(Paragraph(f"题干: {row['题干']}", content_style))
                story.append(Paragraph(f"正确答案: {row['正确答案']}", content_style))
                story.append(Spacer(1, 0.2 * inch))
            
            # 生成PDF
            doc.build(story)
            messagebox.showinfo("成功", f"数据已导出到 {file_path}")
            
        except ImportError:
            messagebox.showerror("错误", "需要安装reportlab库，请运行: pip install reportlab")

    def export_to_txt(self):
        """导出数据到TXT"""
        # 询问保存位置
        file_path = filedialog.asksaveasfilename(
            defaultextension=".txt",
            filetypes=[("Text files", "*.txt"), ("All files", "*.*")],
            title="导出为TXT"
        )
        
        if not file_path:
            return
        
        # 写入数据到TXT文件，使用UTF-8编码
        with open(file_path, 'w', encoding='utf-8') as f:
            f.write("错题本导出\n")
            f.write("="*50 + "\n\n")
            
            for i, row in enumerate(self.data, 1):
                f.write(f"第{i}题\n")
                f.write(f"时间: {row['时间']}\n")
                f.write(f"科目: {row['科目']}\n")
                f.write(f"难度: {row['难度']}\n")
                f.write(f"题干: {row['题干']}\n")
                f.write(f"正确答案: {row['正确答案']}\n")
                f.write(f"附件路径: {row['附件路径'] if row['附件路径'] else '无'}\n")
                f.write("-" * 50 + "\n\n")
        
        messagebox.showinfo("成功", f"数据已导出到 {file_path}")

    def import_from_txt(self):
        """从TXT文件导入数据"""
        # 询问选择TXT文件
        file_path = filedialog.askopenfilename(
            defaultextension=".txt",
            filetypes=[("Text files", "*.txt"), ("All files", "*.*")],
            title="从TXT导入"
        )
        
        if not file_path:
            return
        
        try:
            # 读取TXT文件内容
            with open(file_path, 'r', encoding='utf-8') as f:
                content = f.read()
            
            # 解析TXT内容并导入数据
            imported_data = self.parse_txt_content(content)
            
            if imported_data:
                # 添加到现有数据中
                self.data.extend(imported_data)
                
                # 保存到CSV文件
                self.save_data()
                
                # 刷新界面
                self.refresh_data()
                
                messagebox.showinfo("成功", f"成功导入 {len(imported_data)} 条错题！")
            else:
                messagebox.showwarning("警告", "未找到有效的错题数据，请检查TXT文件格式。")
                
        except Exception as e:
            messagebox.showerror("错误", f"导入失败: {str(e)}")

    def parse_txt_content(self, content):
        """解析TXT文件内容，返回错题数据列表"""
        lines = content.split('\n')
        imported_data = []
        
        # 用于存储当前错题信息的字典
        current_item = {}
        
        i = 0
        while i < len(lines):
            line = lines[i].strip()
            
            # 检查是否是新错题的开始
            if line.startswith("第") and "题" in line:
                # 如果已有current_item，保存之前的数据
                if current_item and '时间' in current_item:
                    # 设置默认值
                    if '附件路径' not in current_item:
                        current_item['附件路径'] = ''
                    if '难度' not in current_item:
                        current_item['难度'] = '中等'
                    imported_data.append(current_item)
                
                # 开始新的错题记录
                current_item = {}
                
            elif line.startswith("时间:"):
                current_item['时间'] = line[3:].strip()  # 去除"时间:"前缀
                
            elif line.startswith("科目:"):
                current_item['科目'] = line[3:].strip()
                
            elif line.startswith("难度:"):
                current_item['难度'] = line[3:].strip()
                
            elif line.startswith("题干:"):
                # 题干可能有多行，需要处理
                current_item['题干'] = line[3:].strip()
                i += 1
                # 继续读取直到遇到下一个字段或分隔线
                while i < len(lines):
                    next_line = lines[i].strip()
                    if (next_line.startswith("正确答案:") or 
                        next_line.startswith("附件路径:") or
                        next_line.startswith("第") and "题" in next_line or
                        next_line.startswith("-" * 10)):  # 分隔线
                        i -= 1  # 回退一行，因为当前行不属于题干
                        break
                    else:
                        # 添加到题干中
                        if '题干' in current_item:
                            current_item['题干'] += "\n" + next_line
                        else:
                            current_item['题干'] = next_line
                    i += 1
                    
            elif line.startswith("正确答案:"):
                # 正确答案可能有多行，需要处理
                current_item['正确答案'] = line[5:].strip()
                i += 1
                # 继续读取直到遇到下一个字段或分隔线
                while i < len(lines):
                    next_line = lines[i].strip()
                    if (next_line.startswith("附件路径:") or
                        next_line.startswith("第") and "题" in next_line or
                        next_line.startswith("-" * 10)):  # 分隔线
                        i -= 1  # 回退一行，因为当前行不属于正确答案
                        break
                    else:
                        # 添加到正确答案中
                        if '正确答案' in current_item:
                            current_item['正确答案'] += "\n" + next_line
                        else:
                            current_item['正确答案'] = next_line
                    i += 1
                    
            elif line.startswith("附件路径:"):
                current_item['附件路径'] = line[5:].strip()
            
            i += 1
        
        # 添加最后一个错题
        if current_item and '时间' in current_item:
            # 设置默认值
            if '附件路径' not in current_item:
                current_item['附件路径'] = ''
            if '难度' not in current_item:
                current_item['难度'] = '中等'
                
            imported_data.append(current_item)
        
        return imported_data

if __name__ == "__main__":
    root = tk.Tk()
    app = MistakeBookApp(root)
    root.mainloop()
