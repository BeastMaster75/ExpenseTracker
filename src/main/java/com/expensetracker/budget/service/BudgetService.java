package ExpenseTracker.SCB.Service;

import ExpenseTracker.SCB.DTO.BudgetRequestDTO;
import ExpenseTracker.SCB.DTO.BudgetResponseDTO;
import ExpenseTracker.SCB.Model.Budget;
import ExpenseTracker.SCB.Model.User;
import ExpenseTracker.SCB.Repository.BudgetRepository;
import ExpenseTracker.SCB.Repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;

    public BudgetService(
            BudgetRepository budgetRepository,
            UserRepository userRepository) {

        this.budgetRepository = budgetRepository;
        this.userRepository = userRepository;
    }

    // Create a new budget
    public BudgetResponseDTO createBudget(BudgetRequestDTO request) {

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Budget budget = new Budget();

        budget.setUser(user);
        budget.setSpending(request.getSpending());
        budget.setAmountLimit(request.getAmountLimit());
        budget.setPeriodMonth(request.getPeriodMonth());

        // New budgets are active by default
        budget.setDeleted(false);

        Budget savedBudget = budgetRepository.save(budget);

        return convertToResponse(savedBudget);
    }

    // Get budget by ID
    public BudgetResponseDTO getBudgetById(Long id) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget not found");
        }

        return convertToResponse(budget);
    }

    // Get all budgets
    public List<BudgetResponseDTO> getAllBudgets() {

        List<Budget> budgets =
                budgetRepository.findAllByDeletedFalse();

        return budgets.stream()
                .map(this::convertToResponse)
                .toList();
    }

    // Update an existing budget
    public BudgetResponseDTO updateBudget(
            Long id,
            BudgetRequestDTO request) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget not found");
        }

        budget.setSpending(request.getSpending());
        budget.setAmountLimit(request.getAmountLimit());
        budget.setPeriodMonth(request.getPeriodMonth());

        Budget updatedBudget = budgetRepository.save(budget);

        return convertToResponse(updatedBudget);
    }

    // Soft delete the budget
    public void deleteBudget(Long id) {

        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Budget not found"));

        if (budget.isDeleted()) {
            throw new RuntimeException("Budget already deleted");
        }

        budget.setDeleted(true);

        budgetRepository.save(budget);
    }

    // Convert Budget entity to response DTO
    private BudgetResponseDTO convertToResponse(Budget budget) {

        BudgetResponseDTO response = new BudgetResponseDTO();

        response.setId(budget.getId());
        response.setUserId(budget.getUser().getId());
        response.setSpending(budget.getSpending());
        response.setAmountLimit(budget.getAmountLimit());
        response.setPeriodMonth(budget.getPeriodMonth());
        response.setCreatedAt(budget.getCreatedAt());
        response.setUpdatedAt(budget.getUpdatedAt());

        return response;
    }
}