package app.ui;

/**
 * 화면이 다시 보일 때 현재 상태(케이스, 기록)로 내용을 새로 채워야 하는 패널.
 * MainFrame은 화면을 JScrollPane으로 감싸 CardLayout에 넣기 때문에 패널 자체의 componentShown이
 * 호출되지 않는다. 그래서 MainFrame이 화면 전환 때 직접 reload()를 부른다.
 */
public interface Reloadable {
    /** 현재 상태로 화면 내용을 다시 채운다. */
    void reload();
}
