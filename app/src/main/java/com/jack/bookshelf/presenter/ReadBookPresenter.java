package com.jack.bookshelf.presenter;

import static android.text.TextUtils.isEmpty;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.hwangjr.rxbus.RxBus;
import com.hwangjr.rxbus.annotation.Subscribe;
import com.hwangjr.rxbus.annotation.Tag;
import com.hwangjr.rxbus.thread.EventThread;
import com.jack.basemvplib.BasePresenterImpl;
import com.jack.basemvplib.BitIntentDataManager;
import com.jack.basemvplib.impl.IView;
import com.jack.bookshelf.DbHelper;
import com.jack.bookshelf.R;
import com.jack.bookshelf.base.observer.MyObserver;
import com.jack.bookshelf.bean.BookChapterBean;
import com.jack.bookshelf.bean.BookShelfBean;
import com.jack.bookshelf.bean.BookSourceBean;
import com.jack.bookshelf.bean.BookmarkBean;
import com.jack.bookshelf.bean.DownloadBookBean;
import com.jack.bookshelf.bean.LocBookShelfBean;
import com.jack.bookshelf.bean.OpenChapterBean;
import com.jack.bookshelf.bean.SearchBookBean;
import com.jack.bookshelf.bean.TwoDataBean;
import com.jack.bookshelf.constant.RxBusTag;
import com.jack.bookshelf.help.BookshelfHelp;
import com.jack.bookshelf.help.ChangeSourceHelp;
import com.jack.bookshelf.model.BookSourceManager;
import com.jack.bookshelf.model.ImportBookModel;
import com.jack.bookshelf.model.SavedSource;
import com.jack.bookshelf.presenter.contract.ReadBookContract;
import com.jack.bookshelf.service.DownloadService;
import com.jack.bookshelf.service.ReadAloudService;
import com.jack.bookshelf.utils.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Read Book Presenter
 * Copyright (c) 2017. 章钦豪. All rights reserved.
 * Edited by Jack251970
 */

public class ReadBookPresenter extends BasePresenterImpl<ReadBookContract.View> implements ReadBookContract.Presenter {
    private final static int OPEN_FROM_OTHER = 0;
    public final static int OPEN_FROM_APP = 1;

    private BookShelfBean bookShelf;
    private ChangeSourceHelp changeSourceHelp;
    private List<BookChapterBean> chapterBeanList = new ArrayList<>();
    private Disposable changeSourceDisposable;

    @Override
    public void initData(Activity activity) {
        Intent intent = activity.getIntent();
        int open_from = intent.getData() != null ? OPEN_FROM_OTHER : OPEN_FROM_APP;
        open_from = intent.getIntExtra("openFrom", open_from);
        mView.setAdd(intent.getBooleanExtra("inBookshelf", true));
        if (open_from == OPEN_FROM_APP) {
            loadBook(intent);
        } else {
            mView.openBookFromOther();
            mView.upMenu();
        }
    }

    @Override
    public void loadBook(Intent intent) {
        Observable.create((ObservableOnSubscribe<BookShelfBean>) e -> {
            if (bookShelf == null) {
                String bookKey = intent.getStringExtra("bookKey");
                if (!isEmpty(bookKey)) {
                    bookShelf = (BookShelfBean) BitIntentDataManager.getInstance().getData(bookKey);
                }
            }
            if (bookShelf == null && !isEmpty(mView.getNoteUrl())) {
                bookShelf = BookshelfHelp.getBook(mView.getNoteUrl());
            }
            if (bookShelf == null) {
                List<BookShelfBean> beans = BookshelfHelp.getAllBook();
                if (beans != null && beans.size() > 0) {
                    bookShelf = beans.get(0);
                }
            }
            if (bookShelf != null && chapterBeanList.isEmpty()) {
                chapterBeanList = BookshelfHelp.getChapterList(bookShelf.getNoteUrl());
            }
            if (bookShelf == null) {
                e.onError(new Exception(StringUtils.getString(R.string.no_book)));
            } else {
                e.onNext(bookShelf);
                e.onComplete();
            }
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new MyObserver<>() {
                    @Override
                    public void onNext(@NonNull BookShelfBean bookShelfBean) {
                        if (isEmpty(bookShelf.getBookInfoBean().getName())) {
                            mView.finish();
                        } else {
                            mView.startLoadingBook();
                            mView.upMenu();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        mView.finish();
                    }
                });
    }

    /**
     * 禁用当前书源
     */
    public void disableDurBookSource() {
        try {
            BookSourceBean bookSourceBean = BookSourceManager.getBookSourceByUrl(bookShelf.getTag());
            if (bookSourceBean != null) {
                bookSourceBean.addGroup("禁用");
                DbHelper.getDaoSession().getBookSourceBeanDao().insertOrReplace(bookSourceBean);
                mView.toast(bookSourceBean.getBookSourceName() + StringUtils.getString(R.string.have_disabled));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public BookChapterBean getDurChapter() {
        if (chapterBeanList.size() == 0) {
            return null;
        }
        if (chapterBeanList.size() > bookShelf.getDurChapter()) {
            return chapterBeanList.get(bookShelf.getDurChapter());
        }
        return chapterBeanList.get(chapterBeanList.size() - 1);
    }

    @Override
    public void saveBook() {
        if (bookShelf != null) {
            AsyncTask.execute(() -> BookshelfHelp.saveBookToShelf(bookShelf));
        }
    }

    @Override
    public void saveProgress() {
        if (bookShelf != null) {
            AsyncTask.execute(() -> {
                bookShelf.setFinalDate(System.currentTimeMillis());
                bookShelf.setHasUpdate(false);
                DbHelper.getDaoSession().getBookShelfBeanDao().insertOrReplace(bookShelf);
                RxBus.get().post(RxBusTag.UPDATE_BOOK_PROGRESS, bookShelf);
            });
        }
    }

    /**
     * APP外部打开
     */
    @Override
    public void openBookFromOther(Activity activity) {
        Uri uri = activity.getIntent().getData();
        getRealFilePath(activity, uri)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribe(new MyObserver<>() {
                    @Override
                    public void onNext(@NonNull String value) {
                        ImportBookModel.getInstance().importBook(new File(value))
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribeOn(Schedulers.io())
                                .subscribe(new MyObserver<>() {
                                    @Override
                                    public void onNext(@NonNull LocBookShelfBean value) {
                                        if (value.getNew())
                                            RxBus.get().post(RxBusTag.HAD_ADD_BOOK, value);
                                        bookShelf = value.getBookShelfBean();
                                        mView.setAdd(BookshelfHelp.isInBookShelf(bookShelf.getNoteUrl()));
                                        mView.startLoadingBook();
                                    }

                                    @Override
                                    public void onError(Throwable e) {
                                        e.printStackTrace();
                                        mView.toast(R.string.open_text_fail);
                                    }
                                });
                    }

                    @Override
                    public void onError(Throwable e) {
                        e.printStackTrace();
                        mView.toast(R.string.open_text_fail);
                    }
                });
    }

    /**
     * 下载
     */
    @Override
    public void addDownload(int start, int end) {
        addToShelf(() -> {
            DownloadBookBean downloadBook = new DownloadBookBean();
            downloadBook.setName(bookShelf.getBookInfoBean().getName());
            downloadBook.setNoteUrl(bookShelf.getNoteUrl());
            downloadBook.setCoverUrl(bookShelf.getBookInfoBean().getCoverUrl());
            downloadBook.setStart(start);
            downloadBook.setEnd(end);
            downloadBook.setFinalDate(System.currentTimeMillis());
            DownloadService.addDownload(mView.getContext(), downloadBook);
        });
    }

    /**
     * 换源
     */
    @Override
    public void changeBookSource(SearchBookBean searchBook) {
        if (changeSourceDisposable != null && !changeSourceDisposable.isDisposed()) {
            changeSourceDisposable.dispose();
        }
        searchBook.setName(bookShelf.getBookInfoBean().getName());
        searchBook.setAuthor(bookShelf.getBookInfoBean().getAuthor());
        ChangeSourceHelp.changeBookSource(searchBook, bookShelf)
                .subscribe(new MyObserver<>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        super.onSubscribe(d);
                        changeSourceDisposable = d;
                    }

                    @Override
                    public void onNext(@NonNull TwoDataBean<BookShelfBean, List<BookChapterBean>> value) {
                        RxBus.get().post(RxBusTag.HAD_REMOVE_BOOK, bookShelf);
                        RxBus.get().post(RxBusTag.HAD_ADD_BOOK, value);
                        bookShelf = value.getData1();
                        chapterBeanList = value.getData2();
                        mView.changeSourceFinish(bookShelf);
                        String tag = bookShelf.getTag();
                        try {
                            long currentTime = System.currentTimeMillis();
                            String bookName = bookShelf.getBookInfoBean().getName();
                            BookSourceBean bookSourceBean = BookSourceManager.getBookSourceByUrl(tag);
                            if (SavedSource.Instance.getBookSource() != null
                                    && currentTime - SavedSource.Instance.getSaveTime() < 60000
                                    && SavedSource.Instance.getBookName().equals(bookName))
                                SavedSource.Instance.getBookSource().increaseWeight(-450);
                            BookSourceManager.saveBookSource(SavedSource.Instance.getBookSource());
                            SavedSource.Instance.setBookName(bookName);
                            SavedSource.Instance.setSaveTime(currentTime);
                            SavedSource.Instance.setBookSource(bookSourceBean);
                            assert bookSourceBean != null;
                            bookSourceBean.increaseWeightBySelection();
                            BookSourceManager.saveBookSource(bookSourceBean);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        mView.toast(e.getMessage());
                        mView.changeSourceFinish(null);
                    }
                });
    }

    @Override
    public void autoChangeSource() {
        if (changeSourceHelp == null) {
            changeSourceHelp = new ChangeSourceHelp();
        }
        changeSourceHelp.autoChange(bookShelf, new ChangeSourceHelp.ChangeSourceListener() {

            @Override
            public void finish(BookShelfBean bookShelfBean, List<BookChapterBean> chapterBeanList) {
                if (!chapterBeanList.isEmpty()) {
                    RxBus.get().post(RxBusTag.HAD_REMOVE_BOOK, bookShelf);
                    RxBus.get().post(RxBusTag.HAD_ADD_BOOK, bookShelfBean);
                    bookShelf = bookShelfBean;
                    ReadBookPresenter.this.chapterBeanList = chapterBeanList;
                    mView.changeSourceFinish(bookShelf);
                } else {
                    mView.changeSourceFinish(null);
                }
            }

            @Override
            public void error(Throwable throwable) {
                mView.toast(throwable.getMessage());
                mView.changeSourceFinish(null);
            }
        });
    }

    @Override
    public void saveBookmark(BookmarkBean bookmarkBean) {
        Observable.create((ObservableOnSubscribe<BookmarkBean>) e -> {
            BookshelfHelp.saveBookmark(bookmarkBean);
            e.onNext(bookmarkBean);
            e.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public void delBookmark(BookmarkBean bookmarkBean) {
        Observable.create((ObservableOnSubscribe<BookmarkBean>) e -> {
            BookshelfHelp.delBookmark(bookmarkBean);
            e.onNext(bookmarkBean);
            e.onComplete();
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    @Override
    public BookShelfBean getBookShelf() {
        return bookShelf;
    }

    @Override
    public List<BookChapterBean> getChapterList() {
        return chapterBeanList;
    }

    @Override
    public void setChapterList(List<BookChapterBean> chapterList) {
        this.chapterBeanList = chapterList;
        AsyncTask.execute(() -> DbHelper.getDaoSession().getBookChapterBeanDao().insertOrReplaceInTx(chapterList));
    }

    @Override
    public void addToShelf(final OnAddListener addListener) {
        if (bookShelf != null) {
            AsyncTask.execute(() -> {
                BookshelfHelp.saveBookToShelf(bookShelf);
                RxBus.get().post(RxBusTag.HAD_ADD_BOOK, bookShelf);
                mView.setAdd(true);
                if (addListener != null) {
                    addListener.addSuccess();
                }
            });
        }
    }

    @Override
    public void removeFromShelf() {
        if (bookShelf != null) {
            Observable.create((ObservableOnSubscribe<Boolean>) e -> {
                BookshelfHelp.removeFromBookShelf(bookShelf);
                e.onNext(true);
                e.onComplete();
            }).subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new MyObserver<>() {
                        @Override
                        public void onNext(@NonNull Boolean aBoolean) {
                            RxBus.get().post(RxBusTag.HAD_REMOVE_BOOK, bookShelf);
                            mView.setAdd(true);
                            mView.finish();
                        }

                        @Override
                        public void onError(Throwable e) {

                        }
                    });
        }
    }

    private Observable<String> getRealFilePath(final Context context, final Uri uri) {
        return Observable.create(e -> {
            String data = "";
            if (null != uri) {
                final String scheme = uri.getScheme();
                if (scheme == null)
                    data = uri.getPath();
                else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
                    data = uri.getPath();
                } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
                    Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.ImageColumns.DATA}, null, null, null);
                    if (null != cursor) {
                        if (cursor.moveToFirst()) {
                            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
                            if (index > -1) {
                                data = cursor.getString(index);
                            }
                        }
                        cursor.close();
                    }

                    if ((data == null || data.length() <= 0) && uri.getPath() != null && uri.getPath().contains("/storage/emulated/")) {
                        data = uri.getPath().substring(uri.getPath().indexOf("/storage/emulated/"));
                    }
                }
            }
            e.onNext(data == null ? "" : data);
            e.onComplete();
        });
    }

    @Override
    public BookSourceBean getBookSource() {
        if (bookShelf != null) {
            return BookSourceManager.getBookSourceByUrl(bookShelf.getTag());
        }
        return null;
    }

    @Override
    public void attachView(@NonNull IView iView) {
        super.attachView(iView);
        RxBus.get().register(this);
    }

    /////////////////////////////////////////////////

    @Override
    public void detachView() {
        if (changeSourceHelp != null) {
            changeSourceHelp.stopSearch();
        }
        RxBus.get().unregister(this);
    }

    /////////////////////RxBus////////////////////////

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.MEDIA_BUTTON)})
    public void onMediaButton(String command) {
        if (bookShelf != null) {
            mView.onMediaButton(command);
        }
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.UPDATE_READ)})
    public void updateRead(Boolean recreate) {
        mView.refresh(recreate);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.ALOUD_STATE)})
    public void upAloudState(ReadAloudService.Status state) {
        mView.upAloudState(state);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.ALOUD_TIMER)})
    public void upAloudTimer(String timer) {
        mView.upAloudTimer(timer);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.SKIP_TO_CHAPTER)})
    public void skipToChapter(OpenChapterBean openChapterBean) {
        mView.skipToChapter(openChapterBean.getChapterIndex(), openChapterBean.getPageIndex());
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.OPEN_BOOK_MARK)})
    public void openBookmark(BookmarkBean bookmarkBean) {
        mView.showBookmark(bookmarkBean);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.READ_ALOUD_START)})
    public void readAloudStart(Integer start) {
        mView.readAloudStart(start);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.READ_ALOUD_NUMBER)})
    public void readAloudLength(Integer start) {
        mView.readAloudLength(start);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.RECREATE)})
    public void recreate(Boolean recreate) {
        mView.recreate();
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.AUDIO_SIZE)})
    public void upAudioSize(Integer audioSize) {
        mView.upAudioSize(audioSize);
        BookChapterBean bean = chapterBeanList.get(bookShelf.getDurChapter());
        bean.setEnd(Long.valueOf(audioSize));
        DbHelper.getDaoSession().getBookChapterBeanDao().insertOrReplace(bean);
    }

    @Subscribe(thread = EventThread.MAIN_THREAD, tags = {@Tag(RxBusTag.AUDIO_DUR)})
    public void upAudioDur(Integer audioDur) {
        mView.upAudioDur(audioDur);
    }

    public interface OnAddListener {
        void addSuccess();
    }
}
