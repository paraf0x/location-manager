package ua.favn.baseManager.base.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import ua.favn.baseManager.BaseManager;

public abstract class PagedGuiInventory<T> extends GuiInventory {
   protected List<T> subjects;
   private int page;
   private final List<GuiButton> pagedButtons = new ArrayList<>();
   private int previousSlot = -1;
   private int nextSlot = -1;

   public PagedGuiInventory(BaseManager plugin) {
      this(plugin, new ArrayList<>());
   }

   public PagedGuiInventory(BaseManager plugin, List<T> subjects) {
      super(plugin);
      this.subjects = subjects;
   }

   protected void registerPagedButton(GuiButton button) {
      this.pagedButtons.add(button);
   }

   @Override
   public List<GuiButton> getButtons() {
      return Stream.concat(super.getButtons().stream(), this.pagedButtons.stream()).toList();
   }

   public List<T> getSubjectsInPage() {
      return this.getPage() == this.getLastPage()
         ? this.subjects.subList(this.page * this.getSubjectsPerPage(), this.subjects.size())
         : this.subjects.subList(this.page * this.getSubjectsPerPage(), (this.page + 1) * this.getSubjectsPerPage());
   }

   public int getPage() {
      return this.page;
   }

   public PagedGuiInventory<T> setPage(int page, boolean rebuild) {
      this.page = page;
      if (rebuild) {
         this.build();
      }

      return this;
   }

   public int getLastPage() {
      int size = this.subjects.size();
      if (size == 0) {
         return 0;
      } else {
         int lastPage = size % this.getSubjectsPerPage() == 0 ? size / this.getSubjectsPerPage() : size / this.getSubjectsPerPage() + 1;
         return lastPage - 1;
      }
   }

   @Deprecated
   protected void setPageButtons(int previousSlot, int nextSlot) {
      this.previousSlot = previousSlot;
      this.nextSlot = nextSlot;
   }

   protected void addPageButtons() {
      this.previousSlot = 1;
      this.nextSlot = 1;
   }

   protected abstract int getSubjectsPerPage();
}
