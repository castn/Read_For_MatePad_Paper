package com.jack.bookshelf.view.activity;

import static com.jack.bookshelf.utils.NetworkUtils.isNetWorkAvailable;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.hwangjr.rxbus.RxBus;
import com.jack.bookshelf.DbHelper;
import com.jack.bookshelf.MApplication;
import com.jack.bookshelf.R;
import com.jack.bookshelf.base.BaseViewPagerActivity;
import com.jack.bookshelf.constant.RxBusTag;
import com.jack.bookshelf.databinding.ActivityMainBinding;
import com.jack.bookshelf.help.permission.Permissions;
import com.jack.bookshelf.help.permission.PermissionsCompat;
import com.jack.bookshelf.help.storage.BackupRestoreUi;
import com.jack.bookshelf.help.update.UpdateManager;
import com.jack.bookshelf.model.UpLastChapterModel;
import com.jack.bookshelf.presenter.MainPresenter;
import com.jack.bookshelf.presenter.contract.MainContract;
import com.jack.bookshelf.service.WebService;
import com.jack.bookshelf.utils.StringUtils;
import com.jack.bookshelf.view.fragment.BookListFragment;
import com.jack.bookshelf.widget.dialog.InputDialog;
import com.jack.bookshelf.widget.menu.MoreSettingMenu;
import com.jack.bookshelf.widget.menu.SelectMenu;
import com.jack.bookshelf.widget.viewpager.PaperViewPager;

import java.util.List;

import kotlin.Unit;

/**
 * Main Page
 * Adapt to Huawei MatePad Paper
 * Edited by Jack251970
 */

public class MainActivity extends BaseViewPagerActivity<MainContract.Presenter> implements MainContract.View, BookListFragment.CallbackValue {
    private final int requestSource = 14;

    private ActivityMainBinding binding;
    private int group;
    private long exitTime = 0;
    private boolean resumed = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MoreSettingMenu moreSettingMenu;
    private SelectMenu selectMenu;
    private SelectMenu selectMenuBackup;
    private SelectMenu selectMenuRestore;

    @Override
    protected MainContract.Presenter initInjector() {
        return new MainPresenter();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            resumed = savedInstanceState.getBoolean("resumed");
        }
        group = preferences.getInt("bookshelfGroup", 0);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("resumed", resumed);
    }

    @Override
    protected void onCreateActivity() {
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }

    /**
     * 界面pause后恢复导入网络书籍
     */
    @Override
    public void onResume() {
        super.onResume();
        String shared_url = preferences.getString("shared_url", "");
        if (shared_url.length() > 1) {
            InputDialog.builder(this)
                    .setTitle(getString(R.string.add_book_url))
                    .setDefaultValue(shared_url)
                    .setCallback(new InputDialog.Callback() {
                        @Override
                        public void setInputText(String inputText) {
                            inputText = StringUtils.trim(inputText);
                            mPresenter.addBookUrl(inputText);
                        }

                        @Override
                        public void delete(String value) {}
                    }).show();
            preferences.edit().putString("shared_url", "").apply();
        }
    }

    @Override
    protected void initData() {
        UpdateManager.getInstance(this).clearApkClear();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void initImmersionBar() {
        super.initImmersionBar();
    }

    @Override
    public boolean isRecreate() {
        return isRecreate;
    }

    @Override
    public int getGroup() {
        return group;
    }

    @Override
    public PaperViewPager getViewPager() {
        return mVp;
    }

    @Override
    protected List<Fragment> createFragments() {
        BookListFragment bookListFragmentGrid = null, bookListFragmentList = null;
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof BookListFragment) {
                if (((BookListFragment)fragment).getBookshelfLayout() == 0) {
                    bookListFragmentGrid = (BookListFragment) fragment;
                } else {
                    bookListFragmentList = (BookListFragment) fragment;
                }
            }
        }
        if (bookListFragmentGrid == null) {
            bookListFragmentGrid = new BookListFragment(0);
        }
        if (bookListFragmentList == null) {
            bookListFragmentList = new BookListFragment(1);
        }
        return List.of(bookListFragmentGrid, bookListFragmentList);
    }

    @Override
    protected List<String> createTitles() {
        return null;
    }

    public BookListFragment getBookListFragment() {
        try {
            return (BookListFragment) mFragmentList.get(preferences.getInt("bookshelfLayout", 1));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void bindView() {
        super.bindView();
        // 初始化书架布局图标
        initLayout();
        // 初始化书籍类别图标
        initGroupIcon(group);
        // 更新书籍类别
        upGroup(group);
        // 初始化一级菜单
        initMenu();
        // 左侧边栏菜单
        binding.llBookSourceMain.setOnClickListener(view -> BookSourceActivity.startThis(this, requestSource));
        binding.llReplaceMain.setOnClickListener(view -> ReplaceRuleActivity.startThis(this, null));
        binding.llDownloadMain.setOnClickListener(view -> DownloadActivity.startThis(this));
        binding.llBackupMain.setOnClickListener(view -> {
            if (!selectMenuBackup.isShowing()) {
                selectMenuBackup.show(binding.getRoot());
            }
        });
        binding.llRestoreMain.setOnClickListener(view -> {
            if (!selectMenuRestore.isShowing()) {
                selectMenuRestore.show(binding.getRoot());
            }
        });
        binding.ivSettingMain.setOnClickListener(view -> SettingActivity.startThis(this));
        // 搜索栏
        binding.searchBarMain.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, SearchBookActivity.class)));
        // 导入书籍
        binding.ivImportOnlineMain.setOnClickListener(view -> {
            if (!selectMenu.isShowing()) {
                selectMenu.show(binding.getRoot());
            }
        });
        // 选择书架布局
        binding.ivSelectLayoutMain.setOnClickListener(view -> changeBookshelfLayout());
        // 更多选项
        binding.ivMoreSettingsMain.setOnClickListener(view -> {
            if (!moreSettingMenu.isShowing()) {
                moreSettingMenu.show(binding.getRoot(), binding.ivMoreSettingsMain);
            }
        });
        // 书籍类别切换
        binding.tvAllBooksMain.setOnClickListener(view -> upGroup(0));
        binding.tvChaseBookMain.setOnClickListener(view -> upGroup(1));
        binding.tvFattenBookMain.setOnClickListener(view -> upGroup(2));
        binding.tvEndBookMain.setOnClickListener(view -> upGroup(3));
        binding.tvLocalBookMain.setOnClickListener(view -> upGroup(4));
    }

    /**
     * 初始化一级菜单
     */
    private void initMenu() {
        moreSettingMenu = MoreSettingMenu.builder(this)
                .setMenu(R.array.more_setting_menu_main, R.array.icon_more_setting_menu_main)
                .setOnclick(position -> {
                    switch (position) {
                        case 0:
                            getBookListFragment().refresh();
                            break;
                        case 1:
                            if (!isNetWorkAvailable()) {
                                toast(R.string.network_connection_unavailable, Toast.LENGTH_SHORT);
                            } else {
                                RxBus.get().post(RxBusTag.DOWNLOAD_ALL, 10000);
                            }
                            break;
                        case 2:
                            SelectMenu.builder(MainActivity.this)
                                    .setTitle(getString(R.string.sequence_book))
                                    .setBottomButton(getString(R.string.cancel))
                                    .setMenu(getResources().getStringArray(R.array.sequence_book), preferences.getInt(getString(R.string.pk_bookshelf_px), 0))
                                    .setListener(new SelectMenu.OnItemClickListener() {
                                        @Override
                                        public void forBottomButton() {}

                                        @Override
                                        public void forListItem(int lastChoose, int position) {
                                            if (position != lastChoose) {
                                                preferences.edit().putInt(getString(R.string.pk_bookshelf_px),position).apply();
                                                ((BookListFragment)mFragmentList.get(0)).setBookPx(position);
                                                ((BookListFragment)mFragmentList.get(1)).setBookPx(position);
                                            }
                                        }
                                    }).show(binding.getRoot());
                            break;
                        case 3:
                            if (getBookListFragment() != null) {
                                getBookListFragment().setArrange(true);
                            }
                            break;
                        case 4:
                            boolean startedThisTime = WebService.startThis(MainActivity.this);
                            if (!startedThisTime) {
                                toast(getString(R.string.web_service_already_started));
                            }
                            break;
                    }
                });
        selectMenu = SelectMenu.builder(this)
                .setTitle(getString(R.string.import_book))
                .setBottomButton(getString(R.string.cancel))
                .setMenu(getResources().getStringArray(R.array.import_book))
                .setListener(new SelectMenu.OnItemClickListener() {
                    @Override
                    public void forBottomButton() {}

                    @Override
                    public void forListItem(int lastChoose, int position) {
                        switch (position) {
                            case 0:
                                importLocalBooks();
                                break;
                            case 1:
                                importOnlineBooks();
                                break;
                        }
                    }
                });
        selectMenuBackup = SelectMenu.builder(this)
                .setTitle(getString(R.string.backup))
                .setBottomButton(getString(R.string.cancel))
                .setMenu(getResources().getStringArray(R.array.backup_ways))
                .setListener(new SelectMenu.OnItemClickListener() {
                    @Override
                    public void forBottomButton() {}

                    @Override
                    public void forListItem(int lastChoose, int position) {
                        switch (position) {
                            case 0:
                                BackupRestoreUi.INSTANCE.backup(MainActivity.this, binding.getRoot(), BackupRestoreUi.backupRestoreLocal);
                                break;
                            case 1:
                                BackupRestoreUi.INSTANCE.backup(MainActivity.this, binding.getRoot(), BackupRestoreUi.backupRestoreWebDav);
                                break;
                        }

                    }
                });
        selectMenuRestore = SelectMenu.builder(this)
                .setTitle(getString(R.string.restore))
                .setBottomButton(getString(R.string.cancel))
                .setMenu(getResources().getStringArray(R.array.restore_ways))
                .setListener(new SelectMenu.OnItemClickListener() {
                    @Override
                    public void forBottomButton() {}

                    @Override
                    public void forListItem(int lastChoose, int position) {
                        switch (position) {
                            case 0:
                                BackupRestoreUi.INSTANCE.restore(MainActivity.this, binding.getRoot(), BackupRestoreUi.backupRestoreLocal);
                                break;
                            case 1:
                                BackupRestoreUi.INSTANCE.restore(MainActivity.this, binding.getRoot(), BackupRestoreUi.backupRestoreWebDav);
                                break;
                        }

                    }
                });
    }

    /**
     * 导入本地书籍
     */
    private void importLocalBooks() {
        new PermissionsCompat.Builder(this, binding.getRoot())
                .addPermissions(Permissions.READ_EXTERNAL_STORAGE, Permissions.WRITE_EXTERNAL_STORAGE)
                .rationale(getString(R.string.import_local_book_need_storage_permission))
                .onGranted((requestCode) -> {
                    startActivity(new Intent(MainActivity.this, ImportBookActivity.class));
                    return Unit.INSTANCE;
                })
                .request();
    }

    /**
     * 导入网络书籍
     */
    private void importOnlineBooks() {
        InputDialog.builder(this)
                .setTitle(getString(R.string.add_book_url))
                .setCallback(new InputDialog.Callback() {
                    @Override
                    public void setInputText(String inputText) {
                        inputText = StringUtils.trim(inputText);
                        mPresenter.addBookUrl(inputText);
                    }

                    @Override
                    public void delete(String value) {}
                }).show();
    }

    /**
     * 更新书籍类别
     */
    private void upGroup(int group) {
        if (this.group != group) {
            upGroupIcon(this.group,group);
            SharedPreferences.Editor editor = preferences.edit();
            editor.putInt("bookshelfGroup", group);
            editor.apply();
        }
        this.group = group;
        RxBus.get().post(RxBusTag.UPDATE_GROUP, group);
        RxBus.get().post(RxBusTag.REFRESH_BOOK_LIST, false);
    }

    /**
     * 更新书籍类别图标
     */
    private void upGroupIcon(int old_group,int group) {
        switch (old_group) {
            case 0:
                binding.ivAllBooksIndicatorMain.setVisibility(View.INVISIBLE);
                break;
            case 1:
                binding.ivChaseBookIndicatorMain.setVisibility(View.INVISIBLE);
                break;
            case 2:
                binding.ivFattenBookIndicatorMain.setVisibility(View.INVISIBLE);
                break;
            case 3:
                binding.ivEndBookIndicatorMain.setVisibility(View.INVISIBLE);
                break;
            case 4:
                binding.ivLocalBookIndicatorMain.setVisibility(View.INVISIBLE);
                break;
        }
        initGroupIcon(group);
    }

    /**
     * 初始化书籍类别图标
     */
    private void initGroupIcon(int group) {
        switch (group) {
            case 0:
                binding.ivAllBooksIndicatorMain.setVisibility(View.VISIBLE);
                break;
            case 1:
                binding.ivChaseBookIndicatorMain.setVisibility(View.VISIBLE);
                break;
            case 2:
                binding.ivFattenBookIndicatorMain.setVisibility(View.VISIBLE);
                break;
            case 3:
                binding.ivEndBookIndicatorMain.setVisibility(View.VISIBLE);
                break;
            case 4:
                binding.ivLocalBookIndicatorMain.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * 初始化书架布局
     */
    private void initLayout() {
        if (preferences.getInt("bookshelfLayout", 1) == 0) {
            binding.ivSelectLayoutMain.setImageResource(R.drawable.ic_bookshelf_layout_grid);
            setCurrentItem(0);
        } else {
            binding.ivSelectLayoutMain.setImageResource(R.drawable.ic_bookshelf_layout_list);
            setCurrentItem(1);
        }
    }

    /**
     * 改变书架布局
     */
    private void changeBookshelfLayout() {
        if (preferences.getInt("bookshelfLayout", 1) == 0) {
            preferences.edit().putInt("bookshelfLayout", 1).apply();
        } else {
            preferences.edit().putInt("bookshelfLayout", 0).apply();
        }
        initLayout();
    }

    /**
     * 检查并记录版本号
     */
    private void versionUp() {
        if (preferences.getLong("versionCode", 0) != MApplication.getVersionCode()) {
            preferences.edit().putLong("versionCode", MApplication.getVersionCode()).apply();
        }
    }

    @Override
    protected void firstRequest() {
        if (!isRecreate) {
            versionUp();
        }
        handler.postDelayed(() -> UpLastChapterModel.getInstance().startUpdate(), 60 * 1000);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            exit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 在2秒后再次点击退出程序
     */
    public void exit() {
        if ((System.currentTimeMillis() - exitTime) > 2000) {
            toast( getString(R.string.double_click_exit_read));
            exitTime = System.currentTimeMillis();
        } else {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        UpLastChapterModel.destroy();
        DbHelper.getDaoSession().getBookContentBeanDao().deleteAll();
        super.onDestroy();
    }
}
