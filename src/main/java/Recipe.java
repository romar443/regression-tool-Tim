import java.util.ArrayList;
import java.util.List;

public class Recipe {
    private String title;
    private List<Category> categoryList;

    Recipe(String title){
        this.title = title;
        this.categoryList = new ArrayList<>();
    }

    public void addCategory(Category category){
        categoryList.add(category);
    }
}
